package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/**
 * Le panneau du bouton + : on choisit le chantier (le dernier utilisé est proposé d'office),
 * puis ce qu'on ajoute. La photo du ticket reste l'action principale.
 */
public final class AjouterScreen extends Screen {

    private String chantierId;

    public AjouterScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public boolean estSuperpose() {
        return true;
    }

    @Override
    public View build() {
        List<Chantier> ouverts = new ArrayList<>();
        for (Chantier c : store().chantiers()) if (!Chantier.TERMINE.equals(c.statut) || c.id.equals(chantierId)) ouverts.add(c);
        if (chantierId == null) {
            String dernier = a.dernierChantier();
            for (Chantier c : ouverts) if (c.id.equals(dernier)) chantierId = c.id;
            if (chantierId == null) for (Chantier c : ouverts) if (Chantier.EN_COURS.equals(c.statut)) {
                chantierId = c.id;
                break;
            }
            if (chantierId == null && !ouverts.isEmpty()) chantierId = ouverts.get(0).id;
        }
        Chantier c = chantierId == null ? null : store().charger(chantierId);

        FrameLayout voile = new FrameLayout(a);
        voile.setBackgroundColor(UiKit.VOILE);
        voile.setClickable(true);
        voile.setOnClickListener(v -> a.retour());
        voile.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panneau = UiKit.colonne(a, 14);
        panneau.setTag(UiKit.TAG_PANNEAU);
        panneau.setClickable(true); // ne ferme pas quand on touche le panneau lui-même
        panneau.setPadding(dp(20), dp(10), dp(20), dp(24));
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(UiKit.FOND);
        float r = dp(28);
        fond.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panneau.setBackground(fond);

        View poignee = new View(a);
        poignee.setBackground(UiKit.rounded(UiKit.BORDURE, dp(3), 0, 0));
        LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(dp(44), dp(5));
        lpP.gravity = Gravity.CENTER_HORIZONTAL;
        panneau.addView(poignee, lpP);

        LinearLayout haut = UiKit.ligne(a, 8);
        haut.addView(UiKit.titre(a, "Ajouter", 28), UiKit.poids(1));
        FrameLayout fermer = new FrameLayout(a);
        fermer.setBackground(UiKit.ripple(UiKit.rounded(UiKit.GRIS_FOND, dp(24), 0, 0)));
        fermer.setClickable(true);
        fermer.setContentDescription("Fermer");
        fermer.addView(UiKit.icone(a, R.drawable.ic_close, UiKit.ENCRE, 24), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        fermer.setOnClickListener(v -> a.retour());
        haut.addView(fermer, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panneau.addView(haut, UiKit.pleineLargeur());

        if (c == null) {
            panneau.addView(UiKit.text(a, "Aucun chantier ouvert. Créez-en un dans l'onglet Chantiers.", 17, UiKit.ENCRE_2, false));
            voile.addView(panneau, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            return voile;
        }

        // Sur quel chantier ?
        if (ouverts.size() > 1) {
            panneau.addView(UiKit.text(a, "Sur quel chantier ?", 15, UiKit.ENCRE_2, false));
            LinearLayout puces = UiKit.ligne(a, 8);
            for (Chantier x : ouverts) {
                boolean sel = x.id.equals(chantierId);
                Button b = UiKit.boutonChoix(a, x.titre, sel, UiKit.ENCRE, Color.WHITE, v -> {
                    chantierId = x.id;
                    a.remplacer(this);
                });
                b.setPadding(dp(16), 0, dp(16), 0);
                puces.addView(b);
            }
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(puces);
            panneau.addView(hs, UiKit.pleineLargeur());
        } else {
            panneau.addView(UiKit.text(a, c.titre + (c.client.isEmpty() ? "" : " · " + c.client), 17, UiKit.ENCRE_2, false));
        }

        final String cid = c.id;
        // Photo du ticket : l'action principale, sur toute la largeur.
        panneau.addView(tuile("Photo du ticket ou de la facture", R.drawable.ic_receipt, true, 96, v -> {
            a.noterDernierChantier(cid);
            a.retour();
            a.scannerFacture(new Scanner.Callback() {
                @Override
                public void onFacture(Scanner.Facture facture) {
                    a.ouvrir(DepenseScreen.avecFacture(a, cid, facture));
                }

                @Override
                public void onAnnule() {
                    a.ouvrir(new DepenseScreen(a, cid, null));
                }

                @Override
                public void onErreur(String message) {
                    toast(message);
                    a.ouvrir(new DepenseScreen(a, cid, null));
                }
            });
        }), UiKit.pleineLargeur());

        LinearLayout l1 = UiKit.ligne(a, 10);
        l1.addView(tuile("Heures", R.drawable.ic_horloge, false, 88, v -> ouvrir(cid, new HeuresScreen(a, cid, null))), UiKit.poids(1));
        l1.addView(tuile("Tâche", R.drawable.ic_tache, false, 88, v -> ouvrir(cid, new TacheScreen(a, cid, null, null))), UiKit.poids(1));
        panneau.addView(l1, UiKit.pleineLargeur());
        LinearLayout l2 = UiKit.ligne(a, 10);
        l2.addView(tuile("Photo du chantier", R.drawable.ic_camera, false, 88, v -> {
            a.noterDernierChantier(cid);
            String moment = c.momentParDefaut();
            a.retour();
            a.prendrePhoto(f -> PhotosScreen.enregistrer(a, cid, f, moment, a::rafraichir));
        }), UiKit.poids(1));
        l2.addView(tuile("Note", R.drawable.ic_note, false, 88, v -> ouvrir(cid, new NoteScreen(a, cid, null))), UiKit.poids(1));
        panneau.addView(l2, UiKit.pleineLargeur());
        LinearLayout l3 = UiKit.ligne(a, 10);
        l3.addView(tuile(c.entreProches() ? "Travaux en plus" : "Travaux en plus", R.drawable.ic_plus_box, false, 88,
                v -> ouvrir(cid, new TravauxScreen(a, cid, null))), UiKit.poids(1));
        l3.addView(tuile("Photos de la galerie", R.drawable.ic_photo, false, 88, v -> {
            a.noterDernierChantier(cid);
            String moment = c.momentParDefaut();
            a.retour();
            a.choisirPhotos(f -> PhotosScreen.enregistrer(a, cid, f, moment, a::rafraichir));
        }), UiKit.poids(1));
        panneau.addView(l3, UiKit.pleineLargeur());

        ScrollView sv = new ScrollView(a);
        sv.addView(panneau);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sv.setTag(UiKit.TAG_PANNEAU);
        panneau.setTag(null);
        voile.addView(sv, lp);
        return voile;
    }

    private void ouvrir(String cid, Screen s) {
        a.noterDernierChantier(cid);
        a.retour();
        a.ouvrir(s);
    }

    private View tuile(String label, int icone, boolean principale, int hauteurDp, View.OnClickListener l) {
        LinearLayout t = principale ? UiKit.ligne(a, 14) : UiKit.colonne(a, 8);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setBackground(UiKit.ripple(UiKit.rounded(principale ? UiKit.ACTION : UiKit.SURFACE, dp(20), 0, 0)));
        t.setClickable(true);
        t.setContentDescription(label);
        int couleur = principale ? Color.WHITE : UiKit.ENCRE;
        t.addView(UiKit.icone(a, icone, couleur, principale ? 36 : 28));
        TextView tv = UiKit.titre(a, label, principale ? 21 : 18);
        tv.setTextColor(couleur);
        tv.setGravity(Gravity.CENTER);
        t.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setMinimumHeight(dp(hauteurDp));
        t.setOnClickListener(l);
        return t;
    }
}
