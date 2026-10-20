package com.gregory.truelle.ui.screens;

import android.view.View;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Tous les PDF créés par l'appli, tous chantiers confondus, les plus récents en premier. */
public final class DocumentsScreen extends Screen {

    static final String DOSSIER_CLIENT = "PDF client";
    static final String DOSSIER_FACTURES = "Factures des dépenses";

    public DocumentsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Outils", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Tous les documents", 30));

        List<Object[]> docs = new ArrayList<>();
        for (Chantier c : store().chantiers()) {
            for (File f : store().fichiers(c.id, DOSSIER_CLIENT)) docs.add(new Object[]{f, c, "Récapitulatif client"});
            for (File f : store().fichiers(c.id, DOSSIER_FACTURES)) docs.add(new Object[]{f, c, "Factures des dépenses"});
        }
        docs.sort((x, y) -> Long.compare(((File) y[0]).lastModified(), ((File) x[0]).lastModified()));
        if (docs.isEmpty()) {
            p.contenu.addView(UiKit.text(a, "Aucun PDF pour l'instant. Ils se créent depuis la fiche du chantier → Documents PDF.", 16, UiKit.ENCRE_2, false));
        }
        Blocs.Groupe g = new Blocs.Groupe(a);
        for (Object[] d : docs) {
            File f = (File) d[0];
            Chantier c = (Chantier) d[1];
            g.ajouter(Blocs.rangee(a, Blocs.pastille(a, R.drawable.ic_doc, UiKit.FOND, UiKit.ENCRE, 40), (String) d[2],
                    c.titre + " · " + FicheScreen.quand(f.lastModified()), null, true, v -> Scanner.ouvrir(a, f)));
        }
        if (!g.vide()) p.contenu.addView(g.vue, UiKit.pleineLargeur());
        return p.racine;
    }
}
