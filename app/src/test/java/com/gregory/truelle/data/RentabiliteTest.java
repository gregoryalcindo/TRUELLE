package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RentabiliteTest {

    private long t = 0;
    private final List<Event> ev = new ArrayList<>();

    private void add(String id, String type, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        ev.add(new Event(id, type, "c1", ++t, "Grégory", m));
    }

    @Test
    public void chantierAuReel_avecMajoration_travauxEtFacturation() {
        add("c", Event.CHANTIER_CREE, "titre", "Salle de bain", "mode", "reel", "majoration_pct", "0");
        add("m", Event.CHANTIER_MODIFIE, "majoration_pct", "10", "budget_materiel_centimes", "200000");
        // Devis : le nôtre signé, un autre refusé, un sous-traitant accepté, un sous-traitant juste reçu.
        add("d1", Event.DEVIS, "sorte", "nous", "statut", "signe", "montant_centimes", "500000", "libelle", "Devis initial");
        add("d2", Event.DEVIS, "sorte", "nous", "statut", "refuse", "montant_centimes", "999900");
        add("d3", Event.DEVIS, "sorte", "sous_traitant", "statut", "signe", "montant_centimes", "120000");
        add("d4", Event.DEVIS, "sorte", "sous_traitant", "statut", "recu", "montant_centimes", "80000");
        // Dépenses : une refacturée (mode réel), une forcée en interne.
        add("e1", Event.DEPENSE, "montant_centimes", "100000", "refacturer", "auto");
        add("e2", Event.DEPENSE, "montant_centimes", "5000", "refacturer", "non");
        // Travaux en plus : proposé (ne compte pas), accepté puis fait, accepté.
        add("t1", Event.TRAVAUX, "description", "Niche", "statut", "propose", "montant_centimes", "30000");
        add("t2", Event.TRAVAUX, "description", "Mitigeur", "statut", "accepte", "montant_centimes", "15000");
        add("t3", Event.TRAVAUX, "description", "Faïence", "statut", "propose", "montant_centimes", "40000");
        add("x3", Event.CORRECTION, "cible", "t3", "statut", "accepte", "signature", "Travaux en plus/sig.png");
        add("x2", Event.CORRECTION, "cible", "t2", "statut", "fait");
        // Facturation interne.
        add("f1", Event.FACTURATION, "libelle", "Acompte 30 %", "statut", "encaisse", "montant_centimes", "150000");
        add("f2", Event.FACTURATION, "libelle", "Situation 1", "statut", "facture", "montant_centimes", "200000");
        add("f3", Event.FACTURATION, "libelle", "Solde", "statut", "a_facturer", "montant_centimes", "300000");
        add("s", Event.SUPPRESSION, "cible", "d4");

        Chantier c = Chantier.replay("c1", ev);

        assertEquals(10, c.majorationPct);
        assertEquals(200000, c.budgetMaterielCentimes);
        assertEquals(500000, c.totalDevisSignes());
        assertEquals(55000, c.totalTravauxAcceptes());          // 150 + 400
        assertEquals(110000, c.totalMaterielRefacture());       // 1000 € + 10 %
        assertEquals(665000, c.montantVendu());
        assertEquals(120000, c.totalSousTraitance());
        assertEquals(225000, c.coutTotal());                    // 1050 € de dépenses + 1200 € sous-traitance
        assertEquals(440000, c.marge());
        assertEquals(350000, c.totalFacture());
        assertEquals(150000, c.totalEncaisse());
        assertEquals("Travaux en plus/sig.png", c.element("t3").get("signature"));
        assertNull(c.element("d4"));
        assertEquals(3, c.elements(Event.DEVIS).size());
    }

    @Test
    public void chantierAuForfait_materielNonRefactureSaufException() {
        add("c", Event.CHANTIER_CREE, "titre", "Toiture", "mode", "forfait");
        add("e1", Event.DEPENSE, "montant_centimes", "40000", "refacturer", "auto");
        add("e2", Event.DEPENSE, "montant_centimes", "6000", "refacturer", "oui");
        Chantier c = Chantier.replay("c1", ev);
        assertEquals(6000, c.totalMaterielRefacture());
        assertEquals(6000, c.montantVendu());
        assertEquals(-40000, c.marge());
    }
}
