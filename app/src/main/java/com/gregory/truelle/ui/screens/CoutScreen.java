package com.gregory.truelle.ui.screens;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Postes;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caisse « Coût prévu » : « je prévois environ … », en un montant.
 * Tiroir suivant : « Répartir », par poste (matériaux, main d'œuvre, sous-traitance…).
 */
public final class CoutScreen extends Screen {

    private final String chantierId;
    private boolean reparti;
    private String simple;
    private final Map<String, String> parPoste = new LinkedHashMap<>();
    private boolean initialise;

    public CoutScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Retour", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Coût prévu", 30));
        if (c == null) return p.racine;
        Finance f = a.finance(c);

        // Ce qui vient déjà des lignes de devis signés et des travaux en plus (affiché, pas modifié ici).
        Map<String, Long> depuisDevis = new LinkedHashMap<>();
        Map<String, Long> tout = f.prevuParPoste();
        for (String[] po : Postes.TOUS) {
            long v = tout.get(po[0]) - (c.prevuPostes.containsKey(po[0]) ? c.prevuPostes.get(po[0]) : 0);
            if (po[0].equals(Postes.MATERIAUX) && c.budgetMaterielCentimes > 0 && v == c.budgetMaterielCentimes) v = 0;
            depuisDevis.put(po[0], Math.max(0, v));
        }
        long totalDevis = Finance.somme(depuisDevis);

        if (!initialise) {
            initialise = true;
            reparti = false;
            for (Long v : c.prevuPostes.values()) if (v > 0) reparti = true;
            simple = c.coutPrevuCentimes > 0 ? Money.toInput(c.coutPrevuCentimes) : "";
            for (String[] po : Postes.TOUS) {
                Long v = c.prevuPostes.get(po[0]);
                parPoste.put(po[0], v != null && v > 0 ? Money.toInput(v) : "");
            }
            // En ouvrant « Répartir » depuis un montant simple, on le met d'abord dans les matériaux.
            if (!reparti && c.coutPrevuCentimes > 0) parPoste.put(Postes.MATERIAUX, Money.toInput(c.coutPrevuCentimes));
        }

        p.contenu.addView(UiKit.text(a, "Ce que le chantier devrait vous coûter : achats, location, sous-traitance"
                + (f.proches() ? "." : ", et votre main d'œuvre si vous la comptez."), 16, UiKit.ENCRE_2, false));
        if (totalDevis > 0) {
            p.contenu.addView(UiKit.ligneChiffre(a, "Déjà prévu dans les lignes du devis", Money.format(totalDevis), false), UiKit.pleineLargeur());
        }

        Map<String, EditText> champs = new LinkedHashMap<>();
        EditText fSimple = UiKit.champMontant(a, simple);
        fSimple.setHint("Ex. : 500");
        Runnable garder = () -> {
            simple = fSimple.getText().toString().trim();
            for (Map.Entry<String, EditText> e : champs.entrySet()) parPoste.put(e.getKey(), e.getValue().getText().toString().trim());
        };

        if (!reparti) {
            p.contenu.addView(UiKit.libelle(a, totalDevis > 0 ? "En plus, je prévois environ" : "Je prévois environ", UiKit.euro(a, fSimple)), UiKit.pleineLargeur());
            LinearLayout tiroir = UiKit.carte(a, 8);
            tiroir.setBackground(UiKit.rounded(UiKit.PIERRE, dp(16), 0, 0));
            tiroir.addView(UiKit.text(a, "Envie de suivre le budget poste par poste ?", 16, UiKit.ENCRE, true));
            tiroir.addView(UiKit.boutonContour(a, "Répartir ›", v -> {
                garder.run();
                reparti = true;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            p.contenu.addView(tiroir, UiKit.pleineLargeur());
        } else {
            for (String[] po : Postes.TOUS) {
                EditText e = UiKit.champMontant(a, parPoste.get(po[0]));
                e.setHint("0");
                champs.put(po[0], e);
                long dev = depuisDevis.get(po[0]);
                p.contenu.addView(UiKit.libelle(a, po[1] + (dev > 0 ? " (en plus des " + Money.formatCourt(dev) + " du devis)" : ""), UiKit.euro(a, e)), UiKit.pleineLargeur());
            }
            p.contenu.addView(UiKit.text(a, "Chaque poste a sa barre de budget : alerte à 80 %, rouge au-delà.", 15, UiKit.ENCRE_2, false));
            p.contenu.addView(UiKit.boutonContour(a, "Revenir à un seul montant", v -> {
                garder.run();
                long t = 0;
                for (String s : parPoste.values()) t += s.isEmpty() ? 0 : Math.max(0, Money.parseCentimes(s));
                simple = t > 0 ? Money.toInput(t) : "";
                reparti = false;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            Map<String, String> data = new LinkedHashMap<>();
            if (!reparti) {
                long s = simple.isEmpty() ? 0 : Money.parseCentimes(simple);
                if (s < 0) {
                    fSimple.setError("Montant invalide");
                    return;
                }
                if (s != c.coutPrevuCentimes) data.put("cout_prevu_centimes", Long.toString(s));
                for (String k : c.prevuPostes.keySet()) data.put("prevu_" + k + "_centimes", "0");
            } else {
                for (String[] po : Postes.TOUS) {
                    String s = parPoste.get(po[0]);
                    long val = s == null || s.isEmpty() ? 0 : Money.parseCentimes(s);
                    if (val < 0) {
                        champs.get(po[0]).setError("Montant invalide");
                        return;
                    }
                    Long avant = c.prevuPostes.get(po[0]);
                    if (avant == null || avant != val) data.put("prevu_" + po[0] + "_centimes", Long.toString(val));
                }
                if (c.coutPrevuCentimes != 0) data.put("cout_prevu_centimes", "0");
            }
            if (data.isEmpty() || enregistrer(chantierId, Event.CHANTIER_MODIFIE, data)) {
                toast("Coût prévu enregistré ✓");
                a.retour();
            }
        }));
        return p.racine;
    }
}
