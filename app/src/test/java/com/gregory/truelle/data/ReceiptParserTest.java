package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ReceiptParserTest {

    /** Simule la reconnaissance : une ligne par texte, de haut en bas, toutes à gauche. */
    private static List<ReceiptParser.Ligne> lignes(String... textes) {
        List<ReceiptParser.Ligne> l = new ArrayList<>();
        int y = 0;
        for (String t : textes) {
            l.add(new ReceiptParser.Ligne(t, y, y + 30, 10));
            y += 40;
        }
        return l;
    }

    @Test
    public void ticketPointP_totalTtcTvaDateFournisseur() {
        ReceiptParser.Resultat r = ReceiptParser.analyser(lignes(
                "POINT.P Toulouse Lalande",
                "SIRET 123 456 789 00012",
                "Ticket n° 4521   12/10/2026 08:14",
                "SAC CIMENT 35KG   2 x 9,80   19,60",
                "TOTAL HT          38,25",
                "TVA 20,00 %        7,65",
                "TOTAL TTC         45,90 €",
                "CB                45,90"));
        assertEquals(4590, r.montantCentimes);
        assertEquals(765, r.tvaCentimes);
        assertEquals("12/10/2026", r.date);
        assertEquals("Point P", r.fournisseur);
    }

    @Test
    public void factureAvecMilliersEtNetAPayer() {
        ReceiptParser.Resultat r = ReceiptParser.analyser(lignes(
                "SARL DUPONT MATERIAUX",
                "Facture FA-2026-118 du 03.09.26",
                "Sous-total        1 812,50",
                "Remise              -50,00",
                "Total HT          1 762,50",
                "TVA 20%             352,50",
                "Net à payer       2 115,00"));
        assertEquals(211500, r.montantCentimes);
        assertEquals(35250, r.tvaCentimes);
        assertEquals("03/09/2026", r.date);
        assertEquals("Sarl Dupont Materiaux", r.fournisseur);
    }

    @Test
    public void prixDansUnBlocSepareAligneADroite() {
        List<ReceiptParser.Ligne> l = new ArrayList<>();
        l.add(new ReceiptParser.Ligne("LEROY MERLIN", 0, 30, 10));
        l.add(new ReceiptParser.Ligne("TOTAL A PAYER", 200, 230, 10));
        l.add(new ReceiptParser.Ligne("128,40", 202, 228, 400));   // même hauteur, à droite
        l.add(new ReceiptParser.Ligne("12,00", 300, 330, 400));    // ligne suivante, ignorée
        ReceiptParser.Resultat r = ReceiptParser.analyser(l);
        assertEquals(12840, r.montantCentimes);
        assertEquals("Leroy Merlin", r.fournisseur);
    }

    @Test
    public void sansMotCle_plusGrandMontantHorsDate_etDateFutureIgnoree() {
        ReceiptParser.Resultat r = ReceiptParser.analyser(lignes(
                "Quincaillerie du Centre",
                "Livraison prévue 25/12/2099",
                "vis 4x40       6,20",
                "chevilles      3,15",
                "              9,35"));
        assertEquals(935, r.montantCentimes);
        assertNull(r.date);
        assertEquals("Quincaillerie Du Centre", r.fournisseur);
    }

    @Test
    public void rienDeLisible() {
        ReceiptParser.Resultat r = ReceiptParser.analyser(lignes("~~ ##", "..."));
        assertEquals(-1, r.montantCentimes);
        assertNull(r.date);
    }
}
