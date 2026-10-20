package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Heures;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Postes;
import com.gregory.truelle.data.RecapFin;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.Map;

/**
 * Tableau de bord financier du chantier, recalculé depuis le journal.
 * Budget par poste (prévu / dépensé / engagé / disponible), bénéfice prévu, réel et estimé, facturation.
 * Identique pour un chantier entre proches, sans les lignes de TVA.
 */
public final class RentabiliteScreen extends Screen {

    private final String chantierId;

    public RentabiliteScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Retour", v -> a.retour()));
        if (c == null) return p.racine;
        Finance f = a.finance(c);
        p.entete.addView(UiKit.titre(a, "Budget et bénéfice", 30));
        p.entete.addView(UiKit.text(a, c.titre + (c.client.isEmpty() ? "" : " · " + c.client), 17, UiKit.ENCRE_2, false));
        pro(p, f);
        return p.racine;
    }

    // ------------------------------------------------------------- pro

    private void pro(Page p, Finance f) {
        Finance.Synthese prevu = f.prevu();
        Finance.Synthese reel = f.reel();
        Finance.Synthese estime = f.prevision();

        // Bénéfice estimé en fin de chantier
        LinearLayout carteB = carte();
        carteB.addView(UiKit.text(a, "Bénéfice estimé en fin de chantier", 17, UiKit.ENCRE_2, false));
        TextView m = UiKit.titre(a, Money.format(estime.benefice), 40);
        if (estime.benefice < 0) m.setTextColor(UiKit.ROUGE);
        carteB.addView(m);
        if (estime.venteHt > 0) carteB.addView(UiKit.text(a, Finance.pourcent(estime.beneficeBp()) + (sansTva(f) ? " du montant du chantier" : " du montant hors TVA"), 16, UiKit.ENCRE_2, false));
        TextView etat = etat(f, estime);
        if (etat != null) carteB.addView(etat, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        long conso = f.consommeBp();
        if (conso >= 0) {
            String av = f.c.avancementBp >= 0 ? " · travaux avancés à " + Finance.pourcent(f.c.avancementBp) : "";
            carteB.addView(UiKit.text(a, "Budget consommé : " + Finance.pourcent(conso) + av, 16,
                    f.c.avancementBp >= 0 && conso > f.c.avancementBp + 1000 ? UiKit.ROUGE : UiKit.ENCRE, true));
        }
        p.contenu.addView(carteB, UiKit.pleineLargeur());

        // Budget par poste
        Map<String, Long> budget = f.prevuParPoste();
        Map<String, Long> depense = f.depenseParPoste();
        Map<String, Long> engage = f.engageParPoste();
        LinearLayout postes = carte();
        postes.addView(UiKit.titre(a, "Budget par poste", 20));
        if (Finance.somme(budget) == 0) {
            postes.addView(UiKit.text(a, "Pas encore de budget : indiquez le coût prévu sur les lignes des devis signés.", 15, UiKit.ENCRE_2, false));
        }
        for (String[] po : Postes.TOUS) {
            long b = budget.get(po[0]), d = depense.get(po[0]), e = engage.get(po[0]);
            if (b == 0 && d == 0 && e == 0) continue;
            LinearLayout bloc = UiKit.colonne(a, 4);
            int alerte = f.alerte(po[0]);
            LinearLayout l1 = UiKit.ligne(a, 8);
            l1.addView(UiKit.text(a, po[1], 17, UiKit.ENCRE, true), UiKit.poids(1));
            if (alerte == 2) l1.addView(UiKit.chip(a, "Dépassé", UiKit.ROUGE, Color.WHITE));
            else if (alerte == 1) l1.addView(UiKit.chip(a, "Attention", UiKit.AMBRE_FOND, UiKit.AMBRE));
            bloc.addView(l1, UiKit.pleineLargeur());
            if (b > 0) bloc.addView(barre(d + e, b), UiKit.pleineLargeur());
            String txt = "Dépensé " + Money.format(d) + (e > 0 ? " · engagé " + Money.format(e) : "")
                    + (b > 0 ? " · prévu " + Money.format(b) : " · pas de budget");
            bloc.addView(UiKit.text(a, txt, 15, UiKit.ENCRE_2, false));
            if (b > 0) {
                long dispo = f.disponible(po[0]);
                bloc.addView(UiKit.text(a, dispo >= 0 ? "Disponible : " + Money.format(dispo) : "Dépassement : " + Money.format(-dispo), 15,
                        dispo >= 0 ? UiKit.ENCRE : UiKit.ROUGE, true));
            }
            postes.addView(bloc, UiKit.pleineLargeur());
        }
        if (f.totalHeuresCentiemes() > 0) {
            postes.addView(UiKit.text(a, Heures.format(f.totalHeuresCentiemes()) + " de main d'œuvre saisies.", 15, UiKit.ENCRE_2, false));
        }
        p.contenu.addView(postes, UiKit.pleineLargeur());

        // Prévu / réel / estimé
        LinearLayout tab = carte();
        tab.addView(UiKit.titre(a, "Du coût au bénéfice", 20));
        tab.addView(entete3("", "Prévu", "À ce jour", "Estimé"), UiKit.pleineLargeur());
        tab.addView(ligne3("Coûts du chantier", prevu.deboursSec, reel.deboursSec, estime.deboursSec, false), UiKit.pleineLargeur());
        if (f.r.fraisGenerauxBp > 0) {
            tab.addView(ligne3("Frais généraux " + Finance.pourcent(f.r.fraisGenerauxBp), prevu.fraisGeneraux, reel.fraisGeneraux, estime.fraisGeneraux, false), UiKit.pleineLargeur());
        }
        tab.addView(ligne3("Coût de revient", prevu.coutRevient, reel.coutRevient, estime.coutRevient, true), UiKit.pleineLargeur());
        tab.addView(ligne3(sansTva(f) ? "Vendu" : "Vendu hors TVA", prevu.venteHt, reel.venteHt, estime.venteHt, false), UiKit.pleineLargeur());
        tab.addView(ligne3("Bénéfice", prevu.benefice, reel.benefice, estime.benefice, true), UiKit.pleineLargeur());
        tab.addView(UiKit.text(a, f.proches() ? "Sans TVA : coûts au prix payé, vente au prix convenu."
                : f.recupere()
                ? "Coûts hors TVA (TVA des achats récupérée)."
                : "Coûts au prix payé TVA comprise. Vente hors TVA, car la TVA facturée est reversée.", 14, UiKit.ENCRE_2, false));
        p.contenu.addView(tab, UiKit.pleineLargeur());

        // Facturation
        RecapFin r = RecapFin.construire(f, false);
        LinearLayout fact = carte();
        fact.addView(UiKit.titre(a, "Facturation", 20));
        fact.addView(UiKit.ligneChiffre(a, "Montant du chantier" + (sansTva(f) ? "" : " (TTC)"), Money.format(r.totalTtc), false), UiKit.pleineLargeur());
        fact.addView(UiKit.ligneChiffre(a, "Facturé" + (sansTva(f) ? "" : " (TTC)"), Money.format(f.factureTtc()), false), UiKit.pleineLargeur());
        fact.addView(UiKit.ligneChiffre(a, "Encaissé", Money.format(f.encaisse()), false), UiKit.pleineLargeur());
        fact.addView(UiKit.ligneChiffre(a, "En attente de paiement", Money.format(f.enAttenteDePaiement()), false), UiKit.pleineLargeur());
        if (f.retenues() > 0) fact.addView(UiKit.ligneChiffre(a, "Retenues de garantie", Money.format(f.retenues()), false), UiKit.pleineLargeur());
        fact.addView(UiKit.ligneChiffre(a, "Reste à facturer" + (sansTva(f) ? "" : " (TTC)"), Money.format(Math.max(0, r.totalTtc - f.factureTtc())), true), UiKit.pleineLargeur());
        if (!sansTva(f)) {
            fact.addView(UiKit.ligneChiffre(a, "TVA collectée (à reverser)", Money.format(f.tvaCollectee()), false), UiKit.pleineLargeur());
            if (f.recupere()) fact.addView(UiKit.ligneChiffre(a, "TVA payée sur les achats", Money.format(f.tvaDeductible()), false), UiKit.pleineLargeur());
        }
        p.contenu.addView(fact, UiKit.pleineLargeur());
    }

    private static boolean sansTva(Finance f) {
        return f.sansTvaFacturee();
    }

    private TextView etat(Finance f, Finance.Synthese estime) {
        boolean depasse = false;
        for (String[] po : Postes.TOUS) if (f.alerte(po[0]) == 2) depasse = true;
        if (estime.benefice < 0) return UiKit.chip(a, "Chantier à perte", UiKit.ROUGE, Color.WHITE);
        if (depasse) return UiKit.chip(a, "Un poste dépasse son budget", UiKit.ROUGE, Color.WHITE);
        if (f.consommeBp() >= 0) return UiKit.chip(a, "Dans le budget", UiKit.VERT_FOND, UiKit.VERT);
        return null;
    }

    private View entete3(String l, String a1, String a2, String a3) {
        LinearLayout row = UiKit.ligne(a, 6);
        row.addView(UiKit.text(a, l, 14, UiKit.ENCRE_2, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        for (String s : new String[]{a1, a2, a3}) {
            TextView t = UiKit.text(a, s, 14, UiKit.ENCRE_2, true);
            t.setGravity(android.view.Gravity.END);
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private View ligne3(String l, long v1, long v2, long v3, boolean gras) {
        LinearLayout row = UiKit.ligne(a, 6);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(UiKit.text(a, l, 15, UiKit.ENCRE, gras), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        for (long v : new long[]{v1, v2, v3}) {
            TextView t = UiKit.text(a, Money.formatCourt(v), 15, v < 0 ? UiKit.ROUGE : UiKit.ENCRE, gras);
            t.setGravity(android.view.Gravity.END);
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private LinearLayout carte() {
        LinearLayout l = UiKit.carte(a, 10);
        l.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        return l;
    }

    /** Barre « consommé / budget » : noire, bleue à 80 %, rouge au-delà du budget. */
    private View barre(long reel, long prevu) {
        FrameLayout fond = new FrameLayout(a);
        fond.setBackground(UiKit.rounded(UiKit.NEUTRE, dp(6), 0, 0));
        View plein = new View(a);
        int col = reel > prevu ? UiKit.ROUGE : reel * 10 >= prevu * 8 ? UiKit.BLEU : UiKit.ENCRE;
        plein.setBackground(UiKit.rounded(col, dp(6), 0, 0));
        float ratio = Math.min(1f, prevu <= 0 ? 0f : (float) reel / prevu);
        fond.addView(plein, new FrameLayout.LayoutParams(0, dp(12)));
        fond.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) plein.getLayoutParams();
            lp.width = Math.round(fond.getWidth() * ratio);
            plein.setLayoutParams(lp);
        });
        fond.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        return fond;
    }
}
