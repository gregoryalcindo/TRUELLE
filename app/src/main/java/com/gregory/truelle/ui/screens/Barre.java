package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.ui.UiKit;

/**
 * Barre du bas : Carnet · Chantiers · ( + ) · Planning · Outils.
 * Onglet actif : encre, gras et trait au-dessus (pas de couleur : la couleur est réservée aux états).
 */
final class Barre {

    private Barre() {}

    /** @param chantierId chantier proposé d'office dans le panneau +, ou null */
    static View creer(MainActivity a, String actif, String chantierId) {
        FrameLayout cadre = new FrameLayout(a);
        cadre.setClipChildren(false);
        cadre.setClipToPadding(false);

        LinearLayout barre = UiKit.ligne(a, 0);
        barre.setBackgroundColor(UiKit.SURFACE);
        barre.setGravity(Gravity.CENTER_VERTICAL);
        barre.setClipChildren(false);
        View trait = new View(a);
        trait.setBackgroundColor(UiKit.SEPARATEUR);

        barre.addView(onglet(a, "Carnet", R.drawable.ic_carnet, MainActivity.ONGLET_CARNET, actif), UiKit.poids(1));
        barre.addView(onglet(a, "Chantiers", R.drawable.ic_chantiers, MainActivity.ONGLET_CHANTIERS, actif), UiKit.poids(1));
        View place = new View(a);
        barre.addView(place, UiKit.poids(1));
        barre.addView(onglet(a, "Planning", R.drawable.ic_planning, MainActivity.ONGLET_PLANNING, actif), UiKit.poids(1));
        barre.addView(onglet(a, "Outils", R.drawable.ic_outils, MainActivity.ONGLET_OUTILS, actif), UiKit.poids(1));

        FrameLayout.LayoutParams lpBarre = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(a, 76), Gravity.BOTTOM);
        cadre.addView(barre, lpBarre);
        FrameLayout.LayoutParams lpTrait = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(a, 1), Gravity.BOTTOM);
        lpTrait.bottomMargin = UiKit.dp(a, 76);
        cadre.addView(trait, lpTrait);

        // Le + au centre, posé à cheval sur la barre.
        FrameLayout plus = new FrameLayout(a);
        plus.setBackground(UiKit.ripple(UiKit.rounded(UiKit.ACTION, UiKit.dp(a, 32), 0, 0)));
        plus.setElevation(UiKit.dp(a, 6));
        plus.setClickable(true);
        plus.setContentDescription("Ajouter");
        plus.addView(UiKit.icone(a, R.drawable.ic_plus, Color.WHITE, 30), new FrameLayout.LayoutParams(UiKit.dp(a, 30), UiKit.dp(a, 30), Gravity.CENTER));
        plus.setOnClickListener(v -> a.ouvrir(new AjouterScreen(a, chantierId)));
        FrameLayout.LayoutParams lpPlus = new FrameLayout.LayoutParams(UiKit.dp(a, 64), UiKit.dp(a, 64), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lpPlus.bottomMargin = UiKit.dp(a, 34);
        cadre.addView(plus, lpPlus);

        cadre.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(a, 104)));
        return cadre;
    }

    private static View onglet(MainActivity a, String libelle, int icone, String id, String actif) {
        boolean sel = id.equals(actif);
        int couleur = sel ? UiKit.ENCRE : UiKit.ENCRE_2;
        FrameLayout f = new FrameLayout(a);
        LinearLayout col = UiKit.colonne(a, 3);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(UiKit.icone(a, icone, couleur, 26));
        TextView t = UiKit.text(a, libelle, 13, couleur, sel);
        if (!sel) t.setTypeface(Typeface.DEFAULT);
        t.setMaxLines(1);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpIc = (LinearLayout.LayoutParams) col.getChildAt(0).getLayoutParams();
        lpIc.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        f.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (sel) {
            View indic = new View(a);
            indic.setBackground(UiKit.rounded(UiKit.ENCRE, UiKit.dp(a, 2), 0, 0));
            f.addView(indic, new FrameLayout.LayoutParams(UiKit.dp(a, 32), UiKit.dp(a, 3), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        }
        f.setMinimumHeight(UiKit.dp(a, 76));
        f.setBackground(UiKit.ripple(UiKit.rounded(Color.TRANSPARENT, 0, 0, 0)));
        f.setClickable(true);
        f.setContentDescription(libelle + (sel ? ", onglet actif" : ""));
        f.setOnClickListener(v -> {
            if (!sel) a.allerA(id);
        });
        return f;
    }
}
