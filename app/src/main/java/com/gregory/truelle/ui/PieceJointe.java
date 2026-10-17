package com.gregory.truelle.ui;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.ReceiptParser;

import java.io.File;

/**
 * Bloc « document joint » des formulaires (devis…) : aperçu + Voir / Remplacer,
 * ou, s'il n'y a rien, Photographier / Importer un fichier.
 */
public final class PieceJointe {

    private PieceJointe() {}

    public interface Choix {
        /** Nouveau fichier dans le cache ; « lu » n'est rempli que pour une photo (lecture automatique). */
        void onNouveau(File fichier, String extension, ReceiptParser.Resultat lu);
    }

    public static View bloc(MainActivity a, File actuel, String titre, Choix choix) {
        int dp8 = UiKit.dp(a, 8);
        LinearLayout carte = UiKit.carte(a, 12);
        carte.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(a, 16), 0, 0));

        if (actuel != null) {
            LinearLayout haut = UiKit.ligne(a, 14);
            haut.setGravity(Gravity.TOP);
            ImageView vignette = new ImageView(a);
            vignette.setScaleType(ImageView.ScaleType.FIT_START);
            vignette.setBackground(UiKit.rounded(UiKit.NEUTRE, dp8, UiKit.dp(a, 1), UiKit.BORDURE));
            vignette.setContentDescription("Aperçu, toucher pour ouvrir");
            Bitmap bmp = Scanner.apercuFichier(actuel, UiKit.dp(a, 96));
            if (bmp != null) vignette.setImageBitmap(bmp);
            vignette.setOnClickListener(v -> Scanner.ouvrir(a, actuel));
            haut.addView(vignette, new LinearLayout.LayoutParams(UiKit.dp(a, 96), UiKit.dp(a, 128)));
            LinearLayout textes = UiKit.colonne(a, 6);
            textes.addView(UiKit.text(a, titre, 18, UiKit.ENCRE, true));
            textes.addView(UiKit.text(a, "Toucher l'aperçu pour l'ouvrir.", 16, UiKit.ENCRE_2, false));
            haut.addView(textes, UiKit.poids(1));
            carte.addView(haut, UiKit.pleineLargeur());

            LinearLayout boutons = UiKit.ligne(a, 8);
            boutons.addView(UiKit.boutonContour(a, "Voir", v -> Scanner.ouvrir(a, actuel)), UiKit.poids(1));
            boutons.addView(UiKit.boutonContour(a, "Remplacer", v -> proposer(a, choix)), UiKit.poids(1.4f));
            carte.addView(boutons, UiKit.pleineLargeur());
        } else {
            carte.addView(UiKit.text(a, titre + " : aucun document", 17, UiKit.ENCRE_2, false));
            LinearLayout boutons = UiKit.ligne(a, 8);
            boutons.addView(UiKit.boutonContour(a, "Photographier", v -> photographier(a, choix)), UiKit.poids(1));
            boutons.addView(UiKit.boutonContour(a, "Importer", v -> importer(a, choix)), UiKit.poids(1));
            carte.addView(boutons, UiKit.pleineLargeur());
        }
        return carte;
    }

    private static void proposer(MainActivity a, Choix choix) {
        new android.app.AlertDialog.Builder(a)
                .setTitle("Remplacer le document")
                .setItems(new String[]{"Photographier", "Importer un fichier (PDF, photo)"}, (d, i) -> {
                    if (i == 0) photographier(a, choix);
                    else importer(a, choix);
                })
                .show();
    }

    private static void photographier(MainActivity a, Choix choix) {
        a.scannerFacture(new Scanner.Callback() {
            @Override
            public void onFacture(Scanner.Facture f) {
                choix.onNouveau(f.pdf, "pdf", f.lu);
            }

            @Override
            public void onAnnule() {
            }

            @Override
            public void onErreur(String message) {
                android.widget.Toast.makeText(a, message, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void importer(MainActivity a, Choix choix) {
        a.choisirFichier((fichier, ext) -> choix.onNouveau(fichier, ext, null));
    }
}
