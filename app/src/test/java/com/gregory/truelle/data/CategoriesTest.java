package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class CategoriesTest {

    @Test
    public void devinerDepuisLeFournisseur() {
        assertEquals(Categories.MATERIEL, Categories.deviner("Point P"));
        assertEquals(Categories.CARBURANT, Categories.deviner("TotalEnergies Blagnac"));
        assertEquals(Categories.LOCATION, Categories.deviner("Kiloutou"));
        assertEquals(Categories.REPAS, Categories.deviner("Boulangerie du Centre"));
        assertEquals(Categories.MATERIEL, Categories.deviner(""));
        assertEquals(Categories.MATERIEL, Categories.normaliser("inconnue"));
    }

    @Test
    public void dossierDesFactures_parCategorie_avecToutesLesFactures() {
        List<Event> ev = new ArrayList<>();
        long[] t = {0};
        java.util.function.BiConsumer<String, String[]> add = (id, kv) -> {
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 1; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
            ev.add(new Event(id, kv[0], "c1", ++t[0], "G", m));
        };
        add.accept("c", new String[]{Event.CHANTIER_CREE, "titre", "Toiture", "mode", "forfait"});
        add.accept("e1", new String[]{Event.DEPENSE, "montant_centimes", "40000", "fournisseur", "Point P", "fichier", "Dépenses/a.pdf"});
        add.accept("e2", new String[]{Event.DEPENSE, "montant_centimes", "6000", "fournisseur", "Total", "categorie", "carburant", "fichier", "Dépenses/b.pdf"});
        add.accept("e3", new String[]{Event.DEPENSE, "montant_centimes", "1500", "fournisseur", "Quincaillerie"});
        add.accept("x", new String[]{Event.CORRECTION, "cible", "e3", "categorie", "divers"});

        Chantier c = Chantier.replay("c1", ev);
        assertEquals(40000, c.totalCategorie(Categories.MATERIEL));
        assertEquals(1500, c.totalCategorie(Categories.DIVERS));

        DossierDepenses d = DossierDepenses.construire(c);
        assertEquals(3, d.sections.size());               // Matériel, Carburant, Divers
        assertEquals("Matériel", d.sections.get(0).titre);
        assertEquals(47500, d.total);
        assertEquals(2, d.annexes.size());                // au forfait aussi : dossier interne
        assertEquals(1, d.sansFacture);
    }
}
