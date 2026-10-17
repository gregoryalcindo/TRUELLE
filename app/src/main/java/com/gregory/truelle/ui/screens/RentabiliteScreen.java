package com.gregory.truelle.ui.screens;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

/** Écran 5 des maquettes : la rentabilité du chantier. Tout est recalculé depuis le journal. */
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
        p.entete.addView(UiKit.retour(a, "Gérer le chantier", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Rentabilité", 30));
        if (c == null) return p.racine;
        p.entete.addView(UiKit.text(a, c.titre + (c.client.isEmpty() ? "" : " · " + c.client), 17, UiKit.ENCRE_2, false));

        long marge = c.marge();
        long vendu = c.montantVendu();
        long depenses = c.totalDepensesCentimes();
        boolean budgetDepasse = c.budgetMaterielCentimes > 0 && depenses > c.budgetMaterielCentimes;

        // Marge
        LinearLayout carteMarge = carte();
        carteMarge.addView(UiKit.text(a, "Marge actuelle", 17, UiKit.ENCRE_2, false));
        TextView m = UiKit.titre(a, Money.format(marge), 40);
        if (marge < 0) m.setTextColor(UiKit.ROUGE);
        carteMarge.addView(m);
        if (vendu > 0) {
            long pct = Math.round(marge * 100.0 / vendu);
            carteMarge.addView(UiKit.text(a, pct + " % du montant du chantier", 16, UiKit.ENCRE_2, false));
        }
        TextView etat;
        if (marge < 0) etat = UiKit.chip(a, "Chantier à perte", UiKit.ROUGE, android.graphics.Color.WHITE);
        else if (budgetDepasse) etat = UiKit.chip(a, "Budget matériel dépassé", UiKit.ROUGE, android.graphics.Color.WHITE);
        else if (c.budgetMaterielCentimes > 0) etat = UiKit.chip(a, "Dans le budget", UiKit.VERT_FOND, UiKit.VERT);
        else etat = null;
        if (etat != null) carteMarge.addView(etat, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        p.contenu.addView(carteMarge, UiKit.pleineLargeur());

        // Ce que le client paie
        LinearLayout vente = carte();
        vente.addView(UiKit.titre(a, "Ce que le client paie", 20));
        vente.addView(UiKit.ligneChiffre(a, "Devis signés", Money.format(c.totalDevisSignes()), false), UiKit.pleineLargeur());
        vente.addView(UiKit.ligneChiffre(a, "Travaux en plus acceptés", Money.format(c.totalTravauxAcceptes()), false), UiKit.pleineLargeur());
        if (c.totalMaterielRefacture() > 0) {
            String lib = "Matériel refacturé" + (c.majorationPct > 0 ? " (+" + c.majorationPct + " %)" : "");
            vente.addView(UiKit.ligneChiffre(a, lib, Money.format(c.totalMaterielRefacture()), false), UiKit.pleineLargeur());
        }
        vente.addView(UiKit.ligneChiffre(a, "Total", Money.format(vendu), true), UiKit.pleineLargeur());
        p.contenu.addView(vente, UiKit.pleineLargeur());

        // Ce que le chantier coûte
        LinearLayout couts = carte();
        couts.addView(UiKit.titre(a, "Ce que le chantier coûte", 20));
        String libDep = "Matériel et achats" + (c.budgetMaterielCentimes > 0 ? " (prévu " + Money.format(c.budgetMaterielCentimes) + ")" : "");
        couts.addView(UiKit.ligneChiffre(a, libDep, Money.format(depenses), false), UiKit.pleineLargeur());
        if (c.budgetMaterielCentimes > 0) couts.addView(barre(depenses, c.budgetMaterielCentimes), UiKit.pleineLargeur());
        couts.addView(UiKit.ligneChiffre(a, "Sous-traitance acceptée", Money.format(c.totalSousTraitance()), false), UiKit.pleineLargeur());
        couts.addView(UiKit.ligneChiffre(a, "Total", Money.format(c.coutTotal()), true), UiKit.pleineLargeur());
        p.contenu.addView(couts, UiKit.pleineLargeur());

        // Facturation
        LinearLayout fact = carte();
        fact.addView(UiKit.titre(a, "Facturation", 20));
        fact.addView(UiKit.ligneChiffre(a, "Facturé", Money.format(c.totalFacture()), false), UiKit.pleineLargeur());
        fact.addView(UiKit.ligneChiffre(a, "Encaissé", Money.format(c.totalEncaisse()), false), UiKit.pleineLargeur());
        fact.addView(UiKit.ligneChiffre(a, "Facturé, pas encore encaissé", Money.format(c.totalFacture() - c.totalEncaisse()), false), UiKit.pleineLargeur());
        fact.addView(UiKit.ligneChiffre(a, "Reste à facturer", Money.format(Math.max(0, vendu - c.totalFacture())), true), UiKit.pleineLargeur());
        p.contenu.addView(fact, UiKit.pleineLargeur());

        p.contenu.addView(UiKit.text(a, "Montants TTC. La marge ne tient pas compte du temps passé ni des charges de l'entreprise.", 15, UiKit.ENCRE_2, false));
        return p.racine;
    }

    private LinearLayout carte() {
        LinearLayout l = UiKit.carte(a, 10);
        l.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        return l;
    }

    /** Barre « réel / prévu » : noire, rouge au-delà du budget. */
    private View barre(long reel, long prevu) {
        FrameLayout fond = new FrameLayout(a);
        fond.setBackground(UiKit.rounded(UiKit.NEUTRE, dp(7), 0, 0));
        View plein = new View(a);
        boolean depasse = reel > prevu;
        plein.setBackground(UiKit.rounded(depasse ? UiKit.ROUGE : UiKit.ENCRE, dp(7), 0, 0));
        float ratio = Math.min(1f, prevu <= 0 ? 0f : (float) reel / prevu);
        fond.addView(plein, new FrameLayout.LayoutParams(0, dp(14)));
        fond.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) plein.getLayoutParams();
            lp.width = Math.round(fond.getWidth() * ratio);
            plein.setLayoutParams(lp);
        });
        fond.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
        return fond;
    }
}
