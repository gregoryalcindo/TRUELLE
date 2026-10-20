package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RecapFinTest {

    private long t = 0;
    private final List<Event> ev = new ArrayList<>();

    private void add(String id, String type, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        ev.add(new Event(id, type, "c1", ++t, "Grégory", m));
    }

    @Test
    public void recap_auReel_avecAnnexesEtSignatures() {
        add("c", Event.CHANTIER_CREE, "titre", "Salle de bain", "mode", "reel", "majoration_pct", "10", "tva_defaut_bp", "0");
        add("d1", Event.DEVIS, "sorte", "nous", "statut", "signe", "montant_centimes", "500000", "libelle", "Devis initial");
        add("d2", Event.DEVIS, "sorte", "sous_traitant", "statut", "signe", "montant_centimes", "120000");
        add("t1", Event.TRAVAUX, "description", "Mitigeur", "statut", "accepte", "montant_centimes", "15000",
                "signe_le", "08/10/2026", "signature", "Travaux en plus/sig.png");
        add("t2", Event.TRAVAUX, "description", "Niche", "statut", "propose", "montant_centimes", "30000");
        add("e1", Event.DEPENSE, "montant_centimes", "10000", "tva_bp", "0", "fournisseur", "Point P", "date", "12/10/2026", "fichier", "Dépenses/a.pdf");
        add("e2", Event.DEPENSE, "montant_centimes", "5000", "tva_bp", "0", "fournisseur", "Essence", "refacturer", "non", "fichier", "Dépenses/b.pdf");
        add("e3", Event.DEPENSE, "montant_centimes", "2000", "tva_bp", "0", "fournisseur", "Quincaillerie");
        add("f1", Event.FACTURATION, "libelle", "Acompte", "statut", "encaisse", "montant_centimes", "150000");

        RecapFin r = RecapFin.construire(new Finance(Chantier.replay("c1", ev), new Reglages()), true);

        assertEquals(3, r.sections.size());
        assertEquals(500000, r.sections.get(0).total);
        assertEquals(15000, r.sections.get(1).total);
        assertEquals(13200, r.sections.get(2).total);          // (100 + 20) € + 10 %
        assertEquals(528200, r.totalTtc);
        assertEquals(528200, r.totalHt);
        assertEquals(378200, r.resteAPayer());
        assertEquals(1, r.annexes.size());                       // seule la facture Point P (refacturée, avec fichier)
        assertTrue(r.annexes.get(0).titre.startsWith("Annexe 1 — Point P"));
        assertEquals(1, r.signatures.size());
    }

    @Test
    public void recap_auForfait_sansFacturesFournisseurs() {
        add("c", Event.CHANTIER_CREE, "titre", "Toiture", "mode", "forfait", "tva_defaut_bp", "0");
        add("d1", Event.DEVIS, "sorte", "nous", "statut", "signe", "montant_centimes", "800000");
        add("e1", Event.DEPENSE, "montant_centimes", "40000", "fichier", "Dépenses/a.pdf");
        RecapFin r = RecapFin.construire(new Finance(Chantier.replay("c1", ev), new Reglages()), true);
        assertEquals(1, r.sections.size());
        assertEquals(0, r.annexes.size());
        assertEquals(800000, r.totalTtc);
    }

    @Test
    public void entreprise_derniereValeurParChamp() throws Exception {
        File root = Files.createTempDirectory("truelle").toFile();
        LocalStore store = new LocalStore(root);
        Map<String, String> a = new LinkedHashMap<>();
        a.put("nom", "Alcindo Bâtiment");
        a.put("siret", "123");
        store.ajouterApp(new Event("e1", Event.ENTREPRISE, null, 1, "Grégory", a));
        Map<String, String> b = new LinkedHashMap<>();
        b.put("siret", "456");
        store.ajouterApp(new Event("e2", Event.ENTREPRISE, null, 2, "Papa", b));
        Map<String, String> infos = new LocalStore(root).entreprise();
        assertEquals("Alcindo Bâtiment", infos.get("nom"));
        assertEquals("456", infos.get("siret"));
    }
}
