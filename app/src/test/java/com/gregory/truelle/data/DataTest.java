package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class DataTest {

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static Event ev(String id, String type, long at, String... kv) {
        return new Event(id, type, "c1", at, "Grégory", map(kv));
    }

    @Test
    public void json_allerRetour_avecAccentsGuillemetsEtRetoursLigne() {
        Map<String, String> m = map("texte", "Faïence \"blanche\"\nau plafond \\ ok", "montant", "12345");
        assertEquals(m, JsonLite.read(JsonLite.write(m)));
    }

    @Test
    public void evenement_allerRetour() {
        Event e = ev("abcdef12-0000", Event.DEPENSE, 1_700_000_000_000L, "montant_centimes", "4590", "fournisseur", "Point P");
        Event back = Event.fromJson(e.toJson());
        assertEquals(e.id, back.id);
        assertEquals(e.type, back.type);
        assertEquals(e.at, back.at);
        assertEquals("Point P", back.get("fournisseur"));
        assertTrue(e.fileName().endsWith("_depense_abcdef12.json"));
    }

    @Test
    public void montants_saisieEtAffichage() {
        assertEquals(1250, Money.parseCentimes("12,50"));
        assertEquals(1250, Money.parseCentimes("12.5"));
        assertEquals(123400, Money.parseCentimes("1 234 €"));
        assertEquals(-1, Money.parseCentimes("abc"));
        assertEquals(-1, Money.parseCentimes("12,345"));
        assertEquals("1 234,50 €", Money.format(123450));
        assertEquals("0,05 €", Money.format(5));
        assertEquals("1234,50", Money.toInput(123450));
    }

    @Test
    public void relecture_statutDepensesCorrectionSuppression() {
        List<Event> events = new ArrayList<>();
        events.add(ev("e1", Event.CHANTIER_CREE, 1, "titre", "Salle de bain", "client", "Dupont", "mode", "reel"));
        events.add(ev("e2", Event.STATUT, 2, "valeur", Chantier.EN_COURS));
        events.add(ev("e3", Event.DEPENSE, 3, "montant_centimes", "10000", "fournisseur", "Point P"));
        events.add(ev("e4", Event.DEPENSE, 4, "montant_centimes", "2500", "fournisseur", "Essence", "refacturer", "non"));
        events.add(ev("e5", Event.CORRECTION, 5, "cible", "e3", "montant_centimes", "12000"));
        events.add(ev("e6", Event.NOTE, 6, "texte", "Livraison jeudi"));
        events.add(ev("e7", Event.SUPPRESSION, 7, "cible", "e6"));
        events.add(ev("e8", "type_futur", 8, "x", "y"));

        // L'ordre de lecture des fichiers ne doit pas changer le résultat.
        Collections.shuffle(events);
        Chantier c = Chantier.replay("c1", events);

        assertEquals("Salle de bain", c.titre);
        assertEquals(Chantier.EN_COURS, c.statut);
        assertEquals(Chantier.MODE_REEL, c.mode);
        assertEquals(2, c.depenses().size());
        assertEquals(14500, c.totalDepensesCentimes());
        assertTrue(c.estARefacturer(c.depense("e3")));
        assertFalse(c.estARefacturer(c.depense("e4")));
        assertNull(c.note("e6"));
        assertEquals("e4", c.derniersAjouts().get(0).id);
    }

    @Test
    public void stockage_creationAjoutRelecture() throws Exception {
        File root = Files.createTempDirectory("truelle").toFile();
        LocalStore store = new LocalStore(root);

        Chantier c = store.creerChantier("Salle de bain", "Dupont", "12 rue des Lilas", Chantier.MODE_FORFAIT, "Grégory");
        assertNotNull(c);
        assertTrue(c.dossier.matches("\\d{4}-001 Salle de bain - Dupont"));
        assertTrue(new File(root, c.dossier + "/Dépenses").isDirectory());

        store.ajouter(c.id, Event.create(Event.DEPENSE, c.id, "Papa", map("montant_centimes", "4590", "fournisseur", "Point P")));
        store.ajouter(c.id, Event.create(Event.STATUT, c.id, "Papa", map("valeur", Chantier.EN_COURS)));

        LocalStore relu = new LocalStore(root);
        Chantier c2 = relu.charger(c.id);
        assertEquals(4590, c2.totalDepensesCentimes());
        assertEquals(Chantier.EN_COURS, c2.statut);
        assertEquals(1, relu.chantiers().size());

        Chantier autre = relu.creerChantier("Toiture", "Martin / Fils", "", Chantier.MODE_REEL, "Grégory");
        assertTrue(autre.dossier.matches("\\d{4}-002 Toiture - Martin Fils"));
        assertEquals(2, relu.chantiers().size());
    }
}
