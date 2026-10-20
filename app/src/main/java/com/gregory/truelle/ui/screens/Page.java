package com.gregory.truelle.ui.screens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.gregory.truelle.ui.UiKit;

/**
 * Gabarit commun des écrans : un en-tête, un contenu qui défile, et un pied fixe
 * où se trouve l'action principale (toujours au même endroit, sous le pouce).
 */
final class Page {

    final LinearLayout racine;
    final LinearLayout entete;
    final LinearLayout contenu;
    final LinearLayout pied;

    Page(Context c) {
        racine = UiKit.colonne(c, 0);
        racine.setBackgroundColor(UiKit.FOND);
        racine.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        entete = UiKit.colonne(c, 6);
        entete.setPadding(UiKit.dp(c, 20), UiKit.dp(c, 16), UiKit.dp(c, 20), UiKit.dp(c, 12));
        racine.addView(entete, UiKit.pleineLargeur());

        ScrollView sv = new ScrollView(c);
        sv.setTag(UiKit.TAG_DEFILE);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        contenu = UiKit.colonne(c, 14);
        contenu.setPadding(UiKit.dp(c, 20), UiKit.dp(c, 4), UiKit.dp(c, 20), UiKit.dp(c, 20));
        sv.addView(contenu, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        racine.addView(sv, UiKit.remplirHauteur());

        pied = UiKit.colonne(c, 10);
        pied.setPadding(UiKit.dp(c, 20), UiKit.dp(c, 8), UiKit.dp(c, 20), UiKit.dp(c, 20));
        racine.addView(pied, UiKit.pleineLargeur());
    }

    /** Écran racine : barre du bas (Carnet · Chantiers · + · Planning · Outils). */
    void barre(com.gregory.truelle.MainActivity a, String onglet) {
        if (pied.getChildCount() == 0) pied.setVisibility(View.GONE);
        racine.addView(Barre.creer(a, onglet, null));
    }

    void ajouterAuPied(View v) {
        pied.addView(v, UiKit.pleineLargeur());
    }
}
