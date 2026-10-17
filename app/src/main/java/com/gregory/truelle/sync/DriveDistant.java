package com.gregory.truelle.sync;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Google Drive (API v3) appelé directement, comme dans VYDEL.
 * Autorisation « drive.file » : l'appli ne voit que les fichiers qu'elle a créés elle-même,
 * sur n'importe quel téléphone connecté au même compte Google.
 */
public final class DriveDistant implements Distant {

    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3";
    private static final String DOSSIER = "application/vnd.google-apps.folder";
    public static final String NOM_RACINE = "Chantiers";

    /** Jeton refusé (expiré) : le gestionnaire de synchro en redemande un et recommence. */
    public static final class JetonExpire extends IOException {
        JetonExpire() {
            super("Jeton Google expiré");
        }
    }

    private final String jeton;
    private String racineId;

    public DriveDistant(String jeton) {
        this.jeton = jeton;
    }

    @Override
    public String racine() throws IOException {
        if (racineId != null) return racineId;
        // Le plus ancien dossier « Chantiers » à la racine : les deux téléphones choisissent le même.
        String q = "name = '" + NOM_RACINE + "' and mimeType = '" + DOSSIER + "' and 'root' in parents and trashed = false";
        JSONObject r = json(get(API + "/files?q=" + enc(q) + "&orderBy=createdTime&fields=files(id)&pageSize=10&spaces=drive"));
        JSONArray files = r.optJSONArray("files");
        if (files != null && files.length() > 0) {
            racineId = files.optJSONObject(0).optString("id");
        } else {
            racineId = creerDossier("root", NOM_RACINE);
        }
        return racineId;
    }

    @Override
    public List<Element> lister(String dossierId) throws IOException {
        List<Element> res = new ArrayList<>();
        String q = "'" + dossierId + "' in parents and trashed = false";
        String page = null;
        do {
            String url = API + "/files?q=" + enc(q) + "&orderBy=createdTime&pageSize=1000&spaces=drive"
                    + "&fields=" + enc("nextPageToken,files(id,name,mimeType)")
                    + (page == null ? "" : "&pageToken=" + enc(page));
            JSONObject r = json(get(url));
            JSONArray files = r.optJSONArray("files");
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.optJSONObject(i);
                    res.add(new Element(f.optString("id"), f.optString("name"), DOSSIER.equals(f.optString("mimeType"))));
                }
            }
            page = r.optString("nextPageToken", null);
            if (page != null && page.isEmpty()) page = null;
        } while (page != null);
        return res;
    }

    @Override
    public String creerDossier(String parentId, String nom) throws IOException {
        try {
            JSONObject meta = new JSONObject();
            meta.put("name", nom);
            meta.put("mimeType", DOSSIER);
            meta.put("parents", new JSONArray().put(parentId));
            HttpURLConnection c = ouvrir(API + "/files?fields=id", "POST");
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            ecrire(c, meta.toString().getBytes(StandardCharsets.UTF_8));
            return json(lire(c)).optString("id");
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void envoyer(String parentId, File fichier) throws IOException {
        try {
            JSONObject meta = new JSONObject();
            meta.put("name", fichier.getName());
            meta.put("parents", new JSONArray().put(parentId));
            String frontiere = "truelle" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream corps = new ByteArrayOutputStream();
            corps.write(("--" + frontiere + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            corps.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            corps.write(("\r\n--" + frontiere + "\r\nContent-Type: " + typeMime(fichier.getName()) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            try (InputStream in = new FileInputStream(fichier)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) corps.write(buf, 0, n);
            }
            corps.write(("\r\n--" + frontiere + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpURLConnection c = ouvrir(UPLOAD + "/files?uploadType=multipart&fields=id", "POST");
            c.setRequestProperty("Content-Type", "multipart/related; boundary=" + frontiere);
            ecrire(c, corps.toByteArray());
            lire(c);
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void telecharger(String fichierId, File destination) throws IOException {
        HttpURLConnection c = ouvrir(API + "/files/" + enc(fichierId) + "?alt=media", "GET");
        int code = c.getResponseCode();
        if (code == 401) throw new JetonExpire();
        if (code >= 300) throw new IOException("Drive : erreur " + code + " au téléchargement");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(destination)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------ HTTP

    private HttpURLConnection ouvrir(String url, String methode) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(methode);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Authorization", "Bearer " + jeton);
        return c;
    }

    private String get(String url) throws IOException {
        return lire(ouvrir(url, "GET"));
    }

    private static void ecrire(HttpURLConnection c, byte[] corps) throws IOException {
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(corps.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(corps);
        }
    }

    private static String lire(HttpURLConnection c) throws IOException {
        try {
            int code = c.getResponseCode();
            if (code == 401) throw new JetonExpire();
            InputStream in = code >= 300 ? c.getErrorStream() : c.getInputStream();
            String texte = "";
            if (in != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                texte = b.toString("UTF-8");
            }
            if (code >= 300) throw new IOException("Drive : erreur " + code + " " + abreger(texte));
            return texte;
        } finally {
            c.disconnect();
        }
    }

    private static JSONObject json(String s) throws IOException {
        try {
            return new JSONObject(s);
        } catch (org.json.JSONException e) {
            throw new IOException("Réponse Drive illisible");
        }
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static String abreger(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    static String typeMime(String nom) {
        String n = nom.toLowerCase();
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
