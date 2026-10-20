package com.gregory.truelle.ui.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Reglages;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.List;

/** Outils : calculs du métier, documents, place sur le téléphone, réglages. */
public final class OutilsScreen extends Screen {

    public OutilsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.titre(a, "Outils", 32));

        p.contenu.addView(sousTitre("Calculs du métier"));
        LinearLayout l1 = UiKit.ligne(a, 10);
        l1.addView(tuile("Carrelage", R.drawable.ic_carrelage, v -> a.ouvrir(new CalculScreen(a, CalculScreen.CARRELAGE))), UiKit.poids(1));
        l1.addView(tuile("Peinture", R.drawable.ic_rouleau, v -> a.ouvrir(new CalculScreen(a, CalculScreen.PEINTURE))), UiKit.poids(1));
        p.contenu.addView(l1, UiKit.pleineLargeur());
        LinearLayout l2 = UiKit.ligne(a, 10);
        l2.addView(tuile("Béton · chape", R.drawable.ic_calcul, v -> a.ouvrir(new CalculScreen(a, CalculScreen.BETON))), UiKit.poids(1));
        l2.addView(tuile("HT ↔ TTC", R.drawable.ic_euro, v -> a.ouvrir(new CalculScreen(a, CalculScreen.TVA))), UiKit.poids(1));
        p.contenu.addView(l2, UiKit.pleineLargeur());

        p.contenu.addView(sousTitre("Au quotidien"));
        Blocs.Groupe g = new Blocs.Groupe(a);
        List<Chantier> tous = store().chantiers();
        int docs = 0;
        for (Chantier c : tous) docs += store().fichiers(c.id, DocumentsScreen.DOSSIER_CLIENT).size() + store().fichiers(c.id, DocumentsScreen.DOSSIER_FACTURES).size();
        g.ajouter(Blocs.rangee(a, Blocs.pastille(a, R.drawable.ic_doc, UiKit.FOND, UiKit.ENCRE, 40), "Tous les documents",
                docs == 0 ? "PDF clients et dossiers de factures" : docs + " PDF, tous chantiers", null, true, v -> a.ouvrir(new DocumentsScreen(a))));
        long octets = 0;
        for (Chantier c : tous) octets += store().taille(c.id);
        g.ajouter(Blocs.rangee(a, Blocs.pastille(a, R.drawable.ic_stockage, UiKit.FOND, UiKit.ENCRE, 40), "Place sur ce téléphone",
                StockageScreen.taille(octets) + " pour les chantiers", null, true, v -> a.ouvrir(new StockageScreen(a))));
        p.contenu.addView(g.vue, UiKit.pleineLargeur());

        p.contenu.addView(sousTitre("Réglages"));
        Blocs.Groupe r = new Blocs.Groupe(a);
        String nom = store().entreprise().get("nom");
        r.ajouter(Blocs.rangee(a, Blocs.pastille(a, R.drawable.ic_entreprise, UiKit.FOND, UiKit.ENCRE, 40), "Entreprise, prénom et Google Drive",
                (nom == null || nom.isEmpty() ? "À renseigner" : nom) + " · " + a.sync().resume(), null, true, v -> a.ouvrir(new AppReglagesScreen(a))));
        Reglages rg = a.reglages();
        String resume = (rg.franchise() ? "Franchise de TVA" : "TVA " + Tva.libelle(rg.tvaDefautBp) + " par défaut")
                + (rg.recupereTva ? " · TVA des achats récupérée" : " · achats comptés TTC")
                + (rg.tauxHoraireCentimes > 0 ? " · " + Money.format(rg.tauxHoraireCentimes) + "/h" : "");
        r.ajouter(Blocs.rangee(a, Blocs.pastille(a, R.drawable.ic_euro, UiKit.FOND, UiKit.ENCRE, 40), "TVA, coûts et validation", resume, null, true,
                v -> a.ouvrir(new ReglagesFinanceScreen(a))));
        p.contenu.addView(r.vue, UiKit.pleineLargeur());

        String version = "";
        try {
            version = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // version inconnue
        }
        TextView v = UiKit.text(a, "TRUELLE " + version, 14, UiKit.ENCRE_2, false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(8), 0, dp(8));
        p.contenu.addView(v, UiKit.pleineLargeur());

        p.barre(a, MainActivity.ONGLET_OUTILS);
        return p.racine;
    }

    private TextView sousTitre(String s) {
        TextView t = UiKit.text(a, s, 15, UiKit.ENCRE_2, true);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private View tuile(String label, int icone, View.OnClickListener l) {
        LinearLayout t = UiKit.colonne(a, 14);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(18), 0, 0)));
        t.addView(Blocs.pastille(a, icone, UiKit.FOND, UiKit.ENCRE, 40));
        TextView tv = UiKit.text(a, label, 16, UiKit.ENCRE, true);
        tv.setGravity(Gravity.START);
        t.addView(tv);
        t.setMinimumHeight(dp(104));
        t.setClickable(true);
        t.setContentDescription(label);
        t.setOnClickListener(l);
        return t;
    }
}
