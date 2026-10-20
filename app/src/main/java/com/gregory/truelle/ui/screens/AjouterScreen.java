package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

/**
 * Écran 3 des maquettes : le panneau du bouton +.
 * Dépense (photo de la facture), Note, Travaux en plus.
 */
public final class AjouterScreen extends Screen {

    private final String chantierId;

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
        Chantier c = store().charger(chantierId);

        FrameLayout voile = new FrameLayout(a);
        voile.setBackgroundColor(UiKit.VOILE);
        voile.setClickable(true);
        voile.setOnClickListener(v -> a.retour());
        voile.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panneau = UiKit.colonne(a, 14);
        panneau.setClickable(true); // ne ferme pas quand on touche le panneau lui-même
        panneau.setPadding(dp(20), dp(24), dp(20), dp(28));
        android.graphics.drawable.GradientDrawable fond = new android.graphics.drawable.GradientDrawable();
        fond.setColor(UiKit.FOND);
        float r = dp(28);
        fond.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panneau.setBackground(fond);

        LinearLayout haut = UiKit.ligne(a, 8);
        haut.addView(UiKit.titre(a, "Ajouter", 28), UiKit.poids(1));
        FrameLayout fermer = new FrameLayout(a);
        fermer.setBackground(UiKit.ripple(UiKit.rounded(UiKit.GRIS_FOND, dp(28), 0, 0)));
        fermer.setClickable(true);
        fermer.setContentDescription("Fermer");
        fermer.addView(UiKit.icone(a, R.drawable.ic_close, UiKit.ENCRE, 26), new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        fermer.setOnClickListener(v -> a.retour());
        haut.addView(fermer, new LinearLayout.LayoutParams(dp(56), dp(56)));
        panneau.addView(haut, UiKit.pleineLargeur());

        if (c != null) panneau.addView(UiKit.text(a, c.titre + (c.client.isEmpty() ? "" : " · " + c.client), 18, UiKit.ENCRE_2, false));

        LinearLayout tuiles = UiKit.ligne(a, 12);
        // Dépense = photo de la facture d'abord. Si on annule la photo, on passe à la saisie à la main.
        tuiles.addView(tuile("Dépense", R.drawable.ic_receipt, true, v -> {
            a.retour();
            a.scannerFacture(new Scanner.Callback() {
                @Override
                public void onFacture(Scanner.Facture facture) {
                    a.ouvrir(DepenseScreen.avecFacture(a, chantierId, facture));
                }

                @Override
                public void onAnnule() {
                    a.ouvrir(new DepenseScreen(a, chantierId, null));
                }

                @Override
                public void onErreur(String message) {
                    toast(message);
                    a.ouvrir(new DepenseScreen(a, chantierId, null));
                }
            });
        }), new LinearLayout.LayoutParams(0, dp(150), 1f));
        tuiles.addView(tuile("Note", R.drawable.ic_note, false, v -> {
            a.retour();
            a.ouvrir(new NoteScreen(a, chantierId, null));
        }), new LinearLayout.LayoutParams(0, dp(150), 1f));
        panneau.addView(tuiles, UiKit.pleineLargeur());
        LinearLayout tuiles2 = UiKit.ligne(a, 12);
        tuiles2.addView(tuile("Heures", R.drawable.ic_clock, false, v -> {
            a.retour();
            a.ouvrir(new HeuresScreen(a, chantierId, null));
        }), new LinearLayout.LayoutParams(0, dp(120), 1f));
        tuiles2.addView(tuile(c != null && c.entreProches() ? "Travaux" : "Travaux en plus", R.drawable.ic_plus_box, false, v -> {
            a.retour();
            a.ouvrir(new TravauxScreen(a, chantierId, null));
        }), new LinearLayout.LayoutParams(0, dp(120), 1f));
        panneau.addView(tuiles2, UiKit.pleineLargeur());
        panneau.addView(UiKit.text(a, "Dépense : photo de la facture ou du ticket, le montant est lu tout seul.", 15, UiKit.ENCRE_2, false));

        voile.addView(panneau, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        return voile;
    }

    private View tuile(String label, int icone, boolean principale, View.OnClickListener l) {
        LinearLayout t = UiKit.colonne(a, 12);
        t.setGravity(Gravity.CENTER);
        t.setBackground(UiKit.ripple(UiKit.rounded(principale ? UiKit.ACTION : UiKit.SURFACE, dp(18), 0, 0)));
        t.setClickable(true);
        t.setContentDescription(label);
        int couleur = principale ? Color.WHITE : UiKit.ENCRE;
        t.addView(UiKit.icone(a, icone, couleur, 48));
        TextView tv = UiKit.titre(a, label, 22);
        tv.setTextColor(couleur);
        tv.setGravity(Gravity.CENTER);
        t.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setOnClickListener(l);
        return t;
    }
}
