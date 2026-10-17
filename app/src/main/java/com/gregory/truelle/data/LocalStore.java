package com.gregory.truelle.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage sur le téléphone, rangé exactement comme le futur dossier Drive :
 *
 *   Chantiers/
 *     _App/
 *     2026-001 Salle de bain - Dupont/
 *       chantier.json
 *       journal/        (un fichier par saisie, jamais modifié)
 *       Dépenses/  Devis/  Travaux en plus/  Photos/  PDF client/
 *     _Archives/
 *
 * La synchronisation Drive (lot 3) n'aura qu'à envoyer et recevoir des fichiers.
 * Aucune dépendance Android ici : la classe se teste sur ordinateur.
 */
public final class LocalStore {

    public static final String APP_DIR = "_App";
    public static final String ARCHIVES_DIR = "_Archives";
    public static final String JOURNAL_DIR = "journal";
    public static final String[] SOUS_DOSSIERS = {"Dépenses", "Devis", "Travaux en plus", "Photos", "PDF client", "Factures des dépenses"};

    private final File root;

    public LocalStore(File root) {
        this.root = root;
        //noinspection ResultOfMethodCallIgnored
        new File(root, APP_DIR).mkdirs();
    }

    public File root() {
        return root;
    }

    /** Crée le dossier du chantier et enregistre l'événement de création. */
    public synchronized Chantier creerChantier(String titre, String client, String adresse, String mode, String auteur) throws IOException {
        String id = UUID.randomUUID().toString();
        String dossier = nomDossier(prochainNumero(), titre, client);
        File dir = new File(root, dossier);
        if (dir.exists()) dir = new File(root, dossier + " " + id.substring(0, 4));
        mkdirs(new File(dir, JOURNAL_DIR));
        for (String s : SOUS_DOSSIERS) mkdirs(new File(dir, s));

        Map<String, String> fiche = new LinkedHashMap<>();
        fiche.put("id", id);
        fiche.put("dossier", dir.getName());
        fiche.put("cree_le", Long.toString(System.currentTimeMillis()));
        writeAtomic(new File(dir, "chantier.json"), JsonLite.write(fiche));

        Map<String, String> data = new LinkedHashMap<>();
        data.put("dossier", dir.getName());
        data.put("titre", titre);
        data.put("client", client);
        data.put("adresse", adresse);
        data.put("mode", Chantier.MODE_REEL.equals(mode) ? Chantier.MODE_REEL : Chantier.MODE_FORFAIT);
        data.put("majoration_pct", "0");
        writeEvent(dir, Event.create(Event.CHANTIER_CREE, id, auteur, data));
        return charger(id);
    }

    /** Ajoute une ligne au journal du chantier. */
    public synchronized void ajouter(String chantierId, Event event) throws IOException {
        File dir = dossierDe(chantierId);
        if (dir == null) throw new IOException("Chantier introuvable : " + chantierId);
        writeEvent(dir, event);
    }

    /**
     * Copie un fichier (facture scannée, devis, signature, PDF…) dans un sous-dossier du chantier,
     * sous un nom lisible donné par NomsFichiers. Si le nom existe déjà : « … (2) », « … (3) »…
     * Retourne le chemin relatif au dossier du chantier, à enregistrer dans le journal.
     */
    public synchronized String importerFichier(String chantierId, String sousDossier, File source, String nom, String extension) throws IOException {
        File dir = dossierDe(chantierId);
        if (dir == null) throw new IOException("Chantier introuvable : " + chantierId);
        File cible = new File(dir, sousDossier);
        mkdirs(cible);
        String base = nettoyerNomFichier(nom);
        if (base.isEmpty()) base = "Document";
        File dest = new File(cible, base + "." + extension);
        for (int i = 2; dest.exists(); i++) dest = new File(cible, base + " (" + i + ")." + extension);
        File tmp = new File(cible, "." + dest.getName() + ".tmp");
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(dest)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Impossible d'enregistrer " + dest.getName());
        }
        return sousDossier + "/" + dest.getName();
    }

    /** Nom de fichier : caractères interdits retirés, espaces simplifiées, 120 caractères au plus. */
    static String nettoyerNomFichier(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (t.startsWith(".")) t = t.substring(1).trim();
        return t.length() > 120 ? t.substring(0, 120).trim() : t;
    }

    /** Fichiers d'un sous-dossier du chantier (ex. « PDF client »), les plus récents en premier. */
    public synchronized List<File> fichiers(String chantierId, String sousDossier) {
        List<File> res = new ArrayList<>();
        File dir = dossierDe(chantierId);
        if (dir == null) return res;
        File[] fs = new File(dir, sousDossier).listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        if (fs != null) for (File f : fs) res.add(f);
        res.sort((x, y) -> y.getName().compareTo(x.getName()));
        return res;
    }

    /** Le fichier réel correspondant à un chemin relatif enregistré dans le journal, ou null. */
    public synchronized File fichier(String chantierId, String cheminRelatif) {
        if (cheminRelatif == null || cheminRelatif.isEmpty() || cheminRelatif.contains("..")) return null;
        File dir = dossierDe(chantierId);
        if (dir == null) return null;
        File f = new File(dir, cheminRelatif);
        return f.exists() ? f : null;
    }

    // ---------------------------------------------------------------- infos de l'entreprise (_App/journal)

    /** Enregistre une modification des infos de l'entreprise (même principe : un fichier, jamais modifié). */
    public synchronized void ajouterApp(Event event) throws IOException {
        writeEvent(new File(root, APP_DIR), event);
    }

    /** Infos de l'entreprise : chaque champ prend sa dernière valeur enregistrée. */
    public synchronized Map<String, String> entreprise() {
        List<Event> events = new ArrayList<>();
        File[] files = new File(new File(root, APP_DIR), JOURNAL_DIR).listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    Event e = Event.fromJson(read(f));
                    if (Event.ENTREPRISE.equals(e.type)) events.add(e);
                } catch (Exception ignored) {
                }
            }
        }
        events.sort(Chantier.ORDER);
        Map<String, String> infos = new LinkedHashMap<>();
        for (Event e : events) infos.putAll(e.data());
        return infos;
    }

    public synchronized Chantier charger(String chantierId) {
        File dir = dossierDe(chantierId);
        return dir == null ? null : lireChantier(dir, chantierId);
    }

    /** Tous les chantiers actifs (hors archives), les plus récemment modifiés en premier. */
    public synchronized List<Chantier> chantiers() {
        List<Chantier> list = new ArrayList<>();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return list;
        for (File dir : dirs) {
            if (dir.getName().startsWith("_")) continue;
            String id = idDe(dir);
            if (id == null) continue;
            list.add(lireChantier(dir, id));
        }
        list.sort((a, b) -> Long.compare(b.modifieLe, a.modifieLe));
        return list;
    }

    // ---------------------------------------------------------------- interne

    private Chantier lireChantier(File dir, String id) {
        List<Event> events = new ArrayList<>();
        File[] files = new File(dir, JOURNAL_DIR).listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    events.add(Event.fromJson(read(f)));
                } catch (Exception ignored) {
                    // Fichier abîmé ou en cours d'écriture : on l'ignore plutôt que de planter.
                }
            }
        }
        Chantier c = Chantier.replay(id, events);
        if (c.dossier.isEmpty()) c.dossier = dir.getName();
        return c;
    }

    private File dossierDe(String chantierId) {
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return null;
        for (File dir : dirs) {
            if (dir.getName().startsWith("_")) continue;
            if (chantierId.equals(idDe(dir))) return dir;
        }
        return null;
    }

    private static String idDe(File dir) {
        File fiche = new File(dir, "chantier.json");
        if (!fiche.exists()) return null;
        try {
            return JsonLite.read(read(fiche)).get("id");
        } catch (Exception e) {
            return null;
        }
    }

    private void writeEvent(File dir, Event e) throws IOException {
        File journal = new File(dir, JOURNAL_DIR);
        mkdirs(journal);
        writeAtomic(new File(journal, e.fileName()), e.toJson());
    }

    private int prochainNumero() {
        int annee = Calendar.getInstance().get(Calendar.YEAR);
        String prefixe = annee + "-";
        int max = 0;
        List<File> dirs = new ArrayList<>();
        File[] actifs = root.listFiles(File::isDirectory);
        if (actifs != null) for (File f : actifs) dirs.add(f);
        File[] archives = new File(root, ARCHIVES_DIR).listFiles(File::isDirectory);
        if (archives != null) for (File f : archives) dirs.add(f);
        for (File d : dirs) {
            String n = d.getName();
            if (n.startsWith(prefixe) && n.length() >= prefixe.length() + 3) {
                try {
                    max = Math.max(max, Integer.parseInt(n.substring(prefixe.length(), prefixe.length() + 3)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max + 1;
    }

    static String nomDossier(int numero, String titre, String client) {
        int annee = Calendar.getInstance().get(Calendar.YEAR);
        String nom = String.format("%d-%03d %s", annee, numero, nettoyer(titre));
        String c = nettoyer(client);
        if (!c.isEmpty()) nom += " - " + c;
        return nom;
    }

    /** Retire les caractères interdits dans un nom de dossier. */
    static String nettoyer(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 60 ? t.substring(0, 60).trim() : t;
    }

    private static void mkdirs(File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Impossible de créer " + dir);
    }

    /** Écrit dans un fichier temporaire puis le renomme : jamais de fichier à moitié écrit. */
    private static void writeAtomic(File target, String content) throws IOException {
        File tmp = new File(target.getParentFile(), "." + target.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Impossible d'enregistrer " + target.getName());
        }
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        }
    }
}
