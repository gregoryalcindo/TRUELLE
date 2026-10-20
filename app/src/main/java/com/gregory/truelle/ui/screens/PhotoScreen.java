package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.ui.Images;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Une photo en grand : moment (avant / pendant / après), couverture, partage, suppression. */
public final class PhotoScreen extends Screen {

    private final String chantierId;
    private final String photoId;
    private boolean telechargement;

    public PhotoScreen(MainActivity a, String chantierId, String photoId) {
        super(a);
        this.chantierId = chantierId;
        this.photoId = photoId;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Element ph = null;
        if (c != null) for (Chantier.Element e : c.photos()) if (e.id.equals(photoId)) ph = e;
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Photos", v -> a.retour()));
        if (c == null || ph == null) {
            p.entete.addView(UiKit.titre(a, "Photo introuvable", 28));
            return p.racine;
        }
        final Chantier.Element photo = ph;
        p.entete.addView(UiKit.titre(a, "Photo " + PhotosScreen.libelleMoment(ph.get("moment")), 26));
        p.entete.addView(UiKit.text(a, (ph.auteur.isEmpty() ? "" : ph.auteur + " · ") + FicheScreen.quand(ph.creeLe), 15, UiKit.ENCRE_2, false));

        // L'image : la photo si elle est sur le téléphone, sinon sa miniature et de quoi la récupérer.
        File grande = store().fichier(chantierId, ph.get("fichier"));
        boolean surDrive = grande == null && store().surLeDriveSeulement(chantierId, ph.get("fichier"));
        Bitmap b = Images.charger(grande != null ? grande : store().fichier(chantierId, ph.get("miniature")), dp(900));
        FrameLayout cadre = new FrameLayout(a);
        cadre.setBackground(UiKit.rounded(UiKit.ENCRE, dp(18), 0, 0));
        cadre.setClipToOutline(true);
        if (b != null) {
            ImageView iv = new ImageView(a);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(b);
            iv.setContentDescription("Photo du chantier");
            cadre.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        } else {
            TextView t = UiKit.text(a, "Photo pas encore reçue sur ce téléphone.", 16, Color.WHITE, false);
            t.setPadding(dp(20), dp(60), dp(20), dp(60));
            t.setGravity(Gravity.CENTER);
            cadre.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        p.contenu.addView(cadre, UiKit.pleineLargeur());
        if (surDrive) {
            p.contenu.addView(UiKit.text(a, "Rangée sur le Drive pour libérer de la place. Ici : la miniature.", 15, UiKit.ENCRE_2, false));
            p.contenu.addView(UiKit.boutonContour(a, telechargement ? "Téléchargement…" : "Télécharger la photo", v -> {
                if (telechargement) return;
                telechargement = true;
                a.remplacer(this);
                File dest = store().emplacement(chantierId, photo.get("fichier"));
                a.sync().recuperer(store().cheminDepuisRacine(chantierId, photo.get("fichier")), dest, (ok, msg) -> {
                    telechargement = false;
                    if (ok) {
                        try {
                            store().recupere(chantierId, photo.get("fichier"));
                        } catch (Exception ignored) {
                            // la liste sera corrigée au prochain essai
                        }
                    } else toast(msg);
                    a.remplacer(this);
                });
            }), UiKit.pleineLargeur());
        }

        // Moment
        LinearLayout blocMoment = UiKit.colonne(a, 8);
        blocMoment.addView(UiKit.text(a, "Moment", 16, UiKit.ENCRE_2, false));
        blocMoment.addView(UiKit.choix(a, new String[][]{{Chantier.MOMENT_AVANT, "Avant"}, {Chantier.MOMENT_PENDANT, "Pendant"}, {Chantier.MOMENT_APRES, "Après"}},
                ph.get("moment").isEmpty() ? Chantier.MOMENT_PENDANT : ph.get("moment"), choisi -> {
                    Map<String, String> d = new LinkedHashMap<>();
                    d.put("cible", photo.id);
                    d.put("moment", choisi);
                    if (enregistrer(chantierId, Event.CORRECTION, d)) a.remplacer(this);
                }), UiKit.pleineLargeur());
        p.contenu.addView(blocMoment, UiKit.pleineLargeur());

        boolean couverture = ph.id.equals(c.photoCouverture() == null ? "" : c.photoCouverture().id) && ph.id.equals(c.couverture);
        LinearLayout actions = UiKit.ligne(a, 10);
        actions.addView(UiKit.boutonContour(a, couverture ? "En couverture ✓" : "Mettre en couverture", v -> {
            if (couverture) return;
            if (enregistrer(chantierId, Event.CHANTIER_MODIFIE, Collections.singletonMap("couverture", photo.id))) {
                a.confirmer("Photo de couverture ✓");
                a.remplacer(this);
            }
        }), UiKit.poids(1));
        if (grande != null) {
            actions.addView(UiKit.boutonContour(a, "Partager", v -> {
                try {
                    Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fichiers", grande);
                    Intent i = new Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(Intent.createChooser(i, "Partager la photo"));
                } catch (Exception e) {
                    toast("Partage impossible : " + e.getMessage());
                }
            }), UiKit.poids(1));
        }
        p.contenu.addView(actions, UiKit.pleineLargeur());

        TextView suppr = UiKit.text(a, "Retirer cette photo du chantier", 16, UiKit.ROUGE, true);
        suppr.setPadding(0, dp(12), 0, dp(12));
        suppr.setMinHeight(dp(48));
        suppr.setOnClickListener(v -> new AlertDialog.Builder(a)
                .setTitle("Retirer cette photo ?")
                .setMessage("Elle n'apparaîtra plus dans le chantier.")
                .setNegativeButton("Non", null)
                .setPositiveButton("Retirer", (d, w) -> {
                    if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", photo.id))) {
                        toast("Photo retirée");
                        a.retour();
                    }
                }).show());
        p.contenu.addView(suppr);
        return p.racine;
    }
}
