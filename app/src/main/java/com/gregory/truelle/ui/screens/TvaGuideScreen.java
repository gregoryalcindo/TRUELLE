package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.TvaGuide;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.function.IntConsumer;

/**
 * « Quel taux de TVA ? » : cinq questions simples, une réponse, des exemples.
 * Ouvert depuis un choix de TVA, il peut appliquer directement le taux trouvé.
 */
public final class TvaGuideScreen extends Screen {

    private final IntConsumer appliquer;
    private TvaGuide.Reponses r = new TvaGuide.Reponses();

    public TvaGuideScreen(MainActivity a, IntConsumer appliquer) {
        super(a);
        this.appliquer = appliquer;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Retour", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Quel taux de TVA ?", 30));

        // Réponses déjà données (touchez pour changer).
        Blocs.Groupe deja = new Blocs.Groupe(a);
        Boolean[] rep = {r.sousTraitant, r.neufOuAgrandissement, r.logementPlusDe2Ans, r.exclu, r.renovationEnergetique};
        for (int i = 0; i < rep.length; i++) {
            if (rep[i] == null) continue;
            final int n = i + 1;
            deja.ajouter(Blocs.rangee(a, null, TvaGuide.question(n), rep[i] ? "Oui" : "Non", null, false, v -> {
                effacerDepuis(n);
                a.remplacer(this);
            }));
        }
        if (!deja.vide()) p.contenu.addView(deja.vue, UiKit.pleineLargeur());

        int q = TvaGuide.prochaineQuestion(r);
        if (q > 0) p.contenu.addView(carteQuestion(q), UiKit.pleineLargeur());
        else p.contenu.addView(carteResultat(TvaGuide.resultat(r)), UiKit.pleineLargeur());

        // À retenir
        p.contenu.addView(Blocs.titreSection(a, "À retenir", null, null), UiKit.pleineLargeur());
        Blocs.Groupe g = new Blocs.Groupe(a);
        g.ajouter(Blocs.rangee(a, null, "5,5 % et 10 % : la mention du client", "Le client recopie et signe la mention sur le devis (ou la facture). Sans elle : 20 % sur tout le chantier.", null, false, null));
        g.ajouter(Blocs.rangee(a, null, "Une ligne par taux", "Sur le devis et la facture, chaque taux sur ses propres lignes (ex. pose à 10 %, lave-vaisselle à 20 %).", null, false, null));
        g.ajouter(Blocs.rangee(a, null, "Moins de 1 000 € TTC d'entretien", "Pas de mention à signer, mais indiquez : client, adresse du logement, nature des travaux, « logement achevé depuis plus de 2 ans ».", null, false, null));
        g.ajouter(Blocs.rangee(a, null, "Garder les papiers", "Gardez le devis signé avec votre comptabilité. Le client garde ses factures jusqu'au 31 décembre de la 5e année.", null, false, null));
        p.contenu.addView(g.vue, UiKit.pleineLargeur());

        // Exemples
        p.contenu.addView(Blocs.titreSection(a, "Exemples", null, null), UiKit.pleineLargeur());
        Blocs.Groupe ex = new Blocs.Groupe(a);
        for (String[] e : TvaGuide.EXEMPLES) ex.ajouter(Blocs.rangee(a, null, e[0], null, e[1], false, null));
        p.contenu.addView(ex.vue, UiKit.pleineLargeur());

        TextView src = UiKit.text(a, "Vérifié par Karim (expert-comptable de l'équipe) : CGI art. 278, 278-0 bis A, 279-0 bis, 283-2 nonies ; "
                + "BOFiP BOI-TVA-LIQ-30-20-90-40 et BOI-LETTRE-000280, état au 8 octobre 2026. France métropolitaine et Corse. "
                + "En cas de doute sur un chantier précis : le service des impôts des entreprises peut trancher.", 13, UiKit.ENCRE_2, false);
        src.setPadding(0, dp(8), 0, dp(8));
        p.contenu.addView(src);
        return p.racine;
    }

    private View carteQuestion(int q) {
        LinearLayout c = UiKit.carte(a, 14);
        c.setBackground(UiKit.rounded(UiKit.SURFACE, dp(20), 0, 0));
        c.addView(UiKit.text(a, "Question " + q, 14, UiKit.ENCRE_2, true));
        c.addView(UiKit.titre(a, TvaGuide.question(q), 22));
        String aide = aide(q);
        if (!aide.isEmpty()) c.addView(UiKit.text(a, aide, 15, UiKit.ENCRE_2, false));
        LinearLayout row = UiKit.ligne(a, 10);
        row.addView(UiKit.bouton(a, "Oui", UiKit.ACTION, Color.WHITE, 60, v -> repondre(q, true)), UiKit.poids(1));
        Button non = UiKit.boutonContour(a, "Non", v -> repondre(q, false));
        non.setMinHeight(dp(60));
        row.addView(non, UiKit.poids(1));
        c.addView(row, UiKit.pleineLargeur());
        return c;
    }

    private static String aide(int q) {
        switch (q) {
            case 1: return "Exemple : une entreprise générale vous confie la plomberie de son chantier.";
            case 2: return "Remise à neuf : on refait la majorité des fondations, murs porteurs, planchers ou de la charpente, ou presque tout le second œuvre (cloisons, sols, plomberie, électricité, menuiseries…). En cas de doute, demandez au service des impôts.";
            case 3: return "Dépendances comprises (garage, cave). Pas pour un bureau, un commerce ou un local professionnel.";
            case 4: return "Ces travaux restent à 20 % même dans un logement ancien.";
            case 5: return "Le matériel doit atteindre les performances fixées par l'arrêté : regardez la fiche technique du produit.";
            default: return "";
        }
    }

    private View carteResultat(TvaGuide.Resultat res) {
        LinearLayout c = UiKit.carte(a, 12);
        c.setBackground(UiKit.rounded(UiKit.PIERRE, dp(20), 0, 0));
        c.addView(UiKit.text(a, "Taux à appliquer", 15, UiKit.ENCRE_2, false));
        TextView t = UiKit.titre(a, res.titre, res.autoliquidation ? 26 : 44);
        c.addView(t);
        c.addView(UiKit.text(a, res.pourquoi, 16, UiKit.ENCRE, false));
        if (!res.aFaireSigner.isEmpty()) {
            LinearLayout alerte = UiKit.ligne(a, 10);
            alerte.setGravity(Gravity.TOP);
            alerte.addView(Blocs.pastille(a, R.drawable.ic_alerte, UiKit.AMBRE_FOND, UiKit.AMBRE, 32));
            alerte.addView(UiKit.text(a, "Le client doit signer la mention sur le devis. Elle apparaît dans le devis, avec un bouton pour la copier.", 15, UiKit.AMBRE, true), UiKit.poids(1));
            c.addView(alerte, UiKit.pleineLargeur());
        }
        if (res.autoliquidation) {
            c.addView(UiKit.text(a, "À régler une fois pour tout le chantier : fiche du chantier → Réglages → « Pour qui travaillez-vous ? » → Une entreprise du bâtiment.", 15, UiKit.ENCRE_2, false));
        } else if (appliquer != null) {
            c.addView(UiKit.bouton(a, "Appliquer " + com.gregory.truelle.data.Tva.libelle(res.tauxBp), UiKit.ACTION, Color.WHITE, 60, v -> {
                a.retour();
                appliquer.accept(res.tauxBp);
            }), UiKit.pleineLargeur());
        }
        c.addView(UiKit.boutonContour(a, "Recommencer", v -> {
            r = new TvaGuide.Reponses();
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        return c;
    }

    private void repondre(int q, boolean oui) {
        switch (q) {
            case 1: r.sousTraitant = oui; break;
            case 2: r.neufOuAgrandissement = oui; break;
            case 3: r.logementPlusDe2Ans = oui; break;
            case 4: r.exclu = oui; break;
            default: r.renovationEnergetique = oui;
        }
        a.remplacer(this);
    }

    private void effacerDepuis(int n) {
        if (n <= 1) r.sousTraitant = null;
        if (n <= 2) r.neufOuAgrandissement = null;
        if (n <= 3) r.logementPlusDe2Ans = null;
        if (n <= 4) r.exclu = null;
        r.renovationEnergetique = null;
    }
}
