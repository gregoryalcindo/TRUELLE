package com.gregory.truelle.ui.screens;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.ui.Images;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Photos du chantier : avant / pendant / après, en grille. */
public final class PhotosScreen extends Screen {

    private final String chantierId;
    private String filtre = "";

    public PhotosScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, c == null ? "Retour" : c.titre, v -> a.retour()));
        if (c == null) return p.racine;
        List<Chantier.Element> toutes = c.photos();
        p.entete.addView(UiKit.titre(a, "Photos", 30));
        p.entete.addView(filtres(toutes), UiKit.pleineLargeur());

        List<Chantier.Element> visibles = new ArrayList<>();
        for (Chantier.Element ph : toutes) if (filtre.isEmpty() || filtre.equals(ph.get("moment"))) visibles.add(ph);
        if (visibles.isEmpty()) {
            p.contenu.addView(UiKit.text(a, toutes.isEmpty()
                    ? "Aucune photo. Avant, pendant, après : elles servent de preuve et peuvent aller dans le PDF client."
                    : "Aucune photo dans cette catégorie.", 16, UiKit.ENCRE_2, false));
        }
        LinearLayout rang = null;
        for (int i = 0; i < visibles.size(); i++) {
            if (i % 3 == 0) {
                rang = UiKit.ligne(a, 8);
                p.contenu.addView(rang, UiKit.pleineLargeur());
            }
            Chantier.Element ph = visibles.get(i);
            rang.addView(case_(c, ph), UiKit.poids(1));
        }
        if (rang != null) for (int k = visibles.size() % 3; k > 0 && k < 3; k++) rang.addView(new View(a), UiKit.poids(1));

        String moment = c.momentParDefaut();
        p.ajouterAuPied(UiKit.boutonAction(a, "Prendre une photo", v -> a.prendrePhoto(f -> enregistrer(a, chantierId, f, moment, () -> a.remplacer(this)))));
        p.ajouterAuPied(UiKit.boutonContour(a, "Choisir dans la galerie", v -> a.choisirPhotos(f -> enregistrer(a, chantierId, f, moment, () -> a.remplacer(this)))));
        return p.racine;
    }

    private View filtres(List<Chantier.Element> toutes) {
        LinearLayout row = UiKit.ligne(a, 4);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackground(UiKit.rounded(UiKit.PIERRE, dp(16), 0, 0));
        String[][] s = {{"", "Toutes"}, {Chantier.MOMENT_AVANT, "Avant"}, {Chantier.MOMENT_PENDANT, "Pendant"}, {Chantier.MOMENT_APRES, "Après"}};
        for (String[] o : s) {
            int n = 0;
            for (Chantier.Element ph : toutes) if (o[0].isEmpty() || o[0].equals(ph.get("moment"))) n++;
            boolean sel = o[0].equals(filtre);
            Button b = UiKit.bouton(a, o[1] + " " + n, sel ? UiKit.SURFACE : Color.TRANSPARENT, sel ? UiKit.ENCRE : UiKit.ENCRE_2, 44, v -> {
                filtre = o[0];
                a.remplacer(this);
            });
            b.setTextSize(14);
            b.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setBackground(UiKit.ripple(UiKit.rounded(sel ? UiKit.SURFACE : Color.TRANSPARENT, dp(12), 0, 0)));
            b.setPadding(dp(2), 0, dp(2), 0);
            row.addView(b, UiKit.poids(1));
        }
        return row;
    }

    private View case_(Chantier c, Chantier.Element ph) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(UiKit.rounded(UiKit.PIERRE, dp(14), 0, 0));
        f.setClipToOutline(true);
        File mini = store().fichier(c.id, ph.get("miniature"));
        Bitmap b = Images.charger(mini, dp(160));
        if (b != null) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
            f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (ph.id.equals(c.couverture)) {
            TextView t = Blocs.etiquette(a, "Couverture", 0);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM | android.view.Gravity.START);
            lp.setMargins(dp(6), 0, 0, dp(6));
            f.addView(t, lp);
        }
        f.setLayoutParams(new LinearLayout.LayoutParams(0, dp(108), 1f));
        f.setClickable(true);
        f.setContentDescription("Photo " + libelleMoment(ph.get("moment")));
        f.setOnClickListener(v -> a.ouvrir(new PhotoScreen(a, chantierId, ph.id)));
        return f;
    }

    static String libelleMoment(String m) {
        if (Chantier.MOMENT_AVANT.equals(m)) return "avant travaux";
        if (Chantier.MOMENT_APRES.equals(m)) return "après travaux";
        return "pendant les travaux";
    }

    /**
     * Réduit et enregistre des photos (en arrière-plan : une photo de 50 Mpx prend un instant),
     * puis appelle {@code apres} sur l'écran.
     */
    static void enregistrer(MainActivity a, String chantierId, List<File> fichiers, String moment, Runnable apres) {
        Toast.makeText(a, fichiers.size() > 1 ? "Enregistrement des photos…" : "Enregistrement de la photo…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int ok = 0;
            String erreur = null;
            String dernierId = null;
            for (File f : fichiers) {
                try {
                    Images.Enregistree e = Images.enregistrerPhoto(a, a.store(), chantierId, f);
                    Map<String, String> data = new LinkedHashMap<>();
                    data.put("fichier", e.fichier);
                    data.put("miniature", e.miniature);
                    data.put("moment", moment);
                    Event ev = Event.create(Event.PHOTO, chantierId, a.auteur(), data);
                    a.store().ajouter(chantierId, ev);
                    dernierId = ev.id;
                    ok++;
                } catch (Exception ex) {
                    erreur = ex.getMessage();
                } finally {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            final int n = ok;
            final String err = erreur;
            final String id = dernierId;
            a.runOnUiThread(() -> {
                if (n > 0) {
                    a.apresEnregistrement();
                    if (n == 1 && id != null) a.noterAnnulable(chantierId, id);
                    a.confirmer(n > 1 ? n + " photos ajoutées ✓" : "Photo ajoutée ✓");
                }
                if (err != null) Toast.makeText(a, "Une photo n'a pas pu être enregistrée : " + err, Toast.LENGTH_LONG).show();
                apres.run();
            });
        }).start();
    }
}
