package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class NomsFichiersTest {

    @Test
    public void nomsLisiblesEtCoherents() {
        assertEquals("2026-09-22 - Le Holloco - 10,85 €", NomsFichiers.depense("Le Holloco", "22/09/2026", 1085));
        assertEquals("Devis - Devis initial - 5 000,00 €", NomsFichiers.devis(true, "Devis initial", "", 500000));
        assertEquals("Devis sous-traitant - Martin Plomberie - 1 200,00 €", NomsFichiers.devis(false, "Plomberie", "Martin", 120000));
        assertEquals("2026-01-05", NomsFichiers.dateTri("5/1/2026"));
        assertTrue(NomsFichiers.depense("", "", 100).matches("\\d{4}-\\d{2}-\\d{2} - Dépense - 1,00 €"));
    }

    @Test
    public void importer_nomExistant_ajouteUnNumero_etRetireLesCaracteresInterdits() throws Exception {
        File root = Files.createTempDirectory("truelle").toFile();
        LocalStore store = new LocalStore(root);
        Chantier c = store.creerChantier("Salle de bain", "Dupont", "", Chantier.MODE_REEL, "G");
        File src = File.createTempFile("scan", ".pdf");
        Files.write(src.toPath(), new byte[]{1, 2, 3});

        String nom = NomsFichiers.depense("Point P / Lalande", "22/09/2026", 1085);
        String p1 = store.importerFichier(c.id, "Dépenses", src, nom, "pdf");
        String p2 = store.importerFichier(c.id, "Dépenses", src, nom, "pdf");
        assertEquals("Dépenses/2026-09-22 - Point P Lalande - 10,85 €.pdf", p1);
        assertEquals("Dépenses/2026-09-22 - Point P Lalande - 10,85 € (2).pdf", p2);

        Chantier relu = store.charger(c.id);
        assertTrue(NomsFichiers.pdfFactures(relu).matches("\\d{4}-001 - Factures des dépenses - Salle de bain - \\d{4}-\\d{2}-\\d{2}"));
    }
}
