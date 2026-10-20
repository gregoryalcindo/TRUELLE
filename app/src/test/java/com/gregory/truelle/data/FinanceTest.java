package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FinanceTest {

    private long t = 0;
    private final List<Event> ev = new ArrayList<>();

    private void add(String id, String type, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        ev.add(new Event(id, type, "c1", ++t, "G", m));
    }

    private static Ligne ligne(String d, String q, long pu, int tva, long cout, String poste) {
        Ligne l = new Ligne();
        l.designation = d;
        l.quantiteMilli = Ligne.parseQuantite(q);
        l.puHtCentimes = pu;
        l.tvaBp = tva;
        l.coutPrevuCentimes = cout;
        l.poste = poste;
        return l;
    }

    @Test
    public void tva_calculsEtArrondis() {
        assertEquals(2000, Tva.tva(10000, Tva.NORMAL));
        assertEquals(11000, Tva.ttc(10000, Tva.INTERMEDIAIRE));
        assertEquals(10000, Tva.ht(12000, Tva.NORMAL));
        assertEquals(904, Tva.ht(1085, Tva.NORMAL));            // 10,85 TTC → 9,04 HT
        assertEquals("5,5 %", Tva.libelle(550));
        assertEquals("2,1 %", Tva.libelle(210));
        assertEquals(550, Tva.parse("5,5"));
        assertEquals(1250, Tva.parse("12.5 %"));
        assertEquals(-1, Tva.parse("150"));
        assertEquals(Tva.NORMAL, Tva.deduire(4590, 765));       // 45,90 dont 7,65 de TVA → 20 %
        assertEquals(Tva.INTERMEDIAIRE, Tva.deduire(11000, 1000));
    }

    @Test
    public void lignes_quantitesEtEncodage() {
        assertEquals(1500, Ligne.parseQuantite("1,5"));
        assertEquals("1,5", Ligne.formatQuantite(1500));
        assertEquals("12", Ligne.formatQuantite(12000));
        Ligne l = ligne("Faïence \"blanche\"", "12,5", 4500, 1000, 30000, Postes.MATERIAUX);
        assertEquals(56250, l.totalHt());                        // 12,5 m² × 45 €
        List<Ligne> back = Ligne.decoder(Ligne.encoder(Arrays.asList(l, ligne("Pose", "8", 4000, 1000, 0, Postes.MAIN_OEUVRE))));
        assertEquals(2, back.size());
        assertEquals("Faïence \"blanche\"", back.get(0).designation);
        assertEquals(12500, back.get(0).quantiteMilli);
        assertEquals(Postes.MAIN_OEUVRE, back.get(1).poste);
    }

    @Test
    public void totaux_remiseEtTvaParTaux() {
        List<Ligne> l = Arrays.asList(
                ligne("Isolation", "1", 100000, Tva.REDUIT, 60000, Postes.MATERIAUX),
                ligne("Peinture", "1", 50000, Tva.INTERMEDIAIRE, 0, Postes.MATERIAUX));
        Finance.Totaux t = Finance.totaux(l, 1000, false);       // remise 10 %
        assertEquals(150000, t.htBrut);
        assertEquals(15000, t.remise);
        assertEquals(135000, t.ht);
        assertEquals(4950, (long) t.tvaParTaux.get(Tva.REDUIT)); // 900 € × 5,5 %
        assertEquals(4500, (long) t.tvaParTaux.get(Tva.INTERMEDIAIRE)); // 450 € × 10 %
        assertEquals(144450, t.ttc);
        assertEquals(60000, t.coutPrevu);
        Finance.Totaux auto = Finance.totaux(l, 0, true);        // autoliquidation : pas de TVA
        assertEquals(0, auto.tva);
    }

    @Test
    public void chantier_prevuReelBeneficeFacturation() {
        add("c", Event.CHANTIER_CREE, "titre", "Salle de bain", "mode", "reel", "majoration_pct", "10", "tva_defaut_bp", "1000", "retenue_bp", "500");
        List<Ligne> l = Arrays.asList(
                ligne("Carrelage", "20", 6000, 1000, 70000, Postes.MATERIAUX),       // 1 200 € HT, coût prévu 700
                ligne("Pose", "16", 4000, 1000, 48000, Postes.MAIN_OEUVRE));         // 640 € HT, coût prévu 480
        add("d1", Event.DEVIS, "sorte", "nous", "statut", "signe", "libelle", "Devis initial", "lignes", Ligne.encoder(l));
        add("d2", Event.DEVIS, "sorte", "nous", "statut", "brouillon", "lignes", Ligne.encoder(l));
        add("st", Event.DEVIS, "sorte", "sous_traitant", "statut", "signe", "autoliquidation", "oui",
                "lignes", Ligne.encoder(Arrays.asList(ligne("Plomberie", "1", 30000, 2000, 0, Postes.SOUS_TRAITANCE))));
        // Travaux en plus : 150 € HT à 10 %, accepté.
        add("t1", Event.TRAVAUX, "description", "Niche", "statut", "accepte", "ht_centimes", "15000", "tva_bp", "1000",
                "cout_prevu_centimes", "5000", "poste", Postes.MATERIAUX);
        // Dépenses : 120 € TTC à 20 % (refacturée, poste matériaux), 30 € TTC carburant (interne).
        add("e1", Event.DEPENSE, "montant_centimes", "12000", "tva_bp", "2000", "categorie", "materiel");
        add("e2", Event.DEPENSE, "montant_centimes", "3000", "tva_bp", "2000", "categorie", "carburant", "refacturer", "non", "valide", "non");
        // 10 h à 35 €/h (taux des réglages) + 2 h à 50 €/h.
        add("h1", Event.HEURES, "heures_centiemes", "1000");
        add("h2", Event.HEURES, "heures_centiemes", "200", "taux_centimes", "5000");
        // Facturation : acompte 500 € HT encaissé, situation 600 € HT facturée.
        add("f1", Event.FACTURATION, "type", "acompte", "ht_centimes", "50000", "tva_bp", "1000", "retenue_bp", "0", "statut", "encaisse");
        add("f2", Event.FACTURATION, "type", "situation", "ht_centimes", "60000", "tva_bp", "1000", "retenue_bp", "500", "statut", "facture");

        Reglages r = new Reglages();
        r.recupereTva = true;                                   // ici : TVA récupérée, coûts en HT
        r.tauxHoraireCentimes = 3500;
        r.fraisGenerauxBp = 2000;
        Finance f = new Finance(Chantier.replay("c1", ev), r);

        assertEquals(184000, f.devisSignesHt());
        assertEquals(15000, f.travauxAcceptesHt());
        assertEquals(11000, f.materielRefactureHt());           // 100 € HT + 10 %
        assertEquals(210000, f.venduHt());
        assertEquals(368000, f.chiffreHt());                    // signé + brouillon

        Map<String, Long> prevu = f.prevuParPoste();
        assertEquals(75000L, (long) prevu.get(Postes.MATERIAUX));
        assertEquals(48000L, (long) prevu.get(Postes.MAIN_OEUVRE));

        Map<String, Long> reel = f.reelParPoste();
        assertEquals(10000L, (long) reel.get(Postes.MATERIAUX)); // 120 TTC → 100 HT
        assertEquals(45000L, (long) reel.get(Postes.MAIN_OEUVRE)); // 350 + 100
        assertEquals(30000L, (long) reel.get(Postes.SOUS_TRAITANCE)); // devis accepté, autoliquidé
        assertEquals(2500L, (long) reel.get(Postes.FRAIS_CHANTIER));
        assertEquals(1, f.aVerifier());

        Finance.Synthese s = f.reel();
        assertEquals(87500, s.deboursSec);
        assertEquals(17500, s.fraisGeneraux);
        assertEquals(105000, s.coutRevient);
        assertEquals(105000, s.benefice);
        assertEquals(5000, s.beneficeBp());                      // 50 %

        assertEquals(110000, f.factureHt());
        assertEquals(55000, f.encaisse());                       // 500 € HT + 10 %
        assertEquals(3300, f.retenues());                        // 5 % de 660 € TTC
        assertEquals(62700, f.enAttenteDePaiement());
        assertEquals(100000, f.resteAFacturerHt());
        assertEquals(11000, f.tvaCollectee());
        assertEquals(2500, f.tvaDeductible());

        RecapFin recap = RecapFin.construire(f, true);
        assertEquals(210000, recap.totalHt);
        assertEquals(21000, recap.totalTva);                     // tout à 10 % (taux du chantier)
        assertEquals(231000, recap.totalTtc);
        assertEquals(231000 - 55000 - 3300, recap.resteAPayer());
    }

    @Test
    public void anciensMontantsTtc_convertisAuTauxDuChantier_etFranchise() {
        add("c", Event.CHANTIER_CREE, "titre", "Toiture", "mode", "forfait", "tva_defaut_bp", "2000");
        add("d1", Event.DEVIS, "sorte", "nous", "statut", "signe", "montant_centimes", "120000");   // ancien : TTC
        add("e1", Event.DEPENSE, "montant_centimes", "4590", "tva_centimes", "765");                 // TVA lue
        Finance f = new Finance(Chantier.replay("c1", ev), new Reglages());
        assertEquals(100000, f.devisSignesHt());
        assertEquals(3825, Finance.htDepense(f.c.depense("e1")));
        assertEquals(Tva.NORMAL, Finance.tvaDepense(f.c.depense("e1")));

        Reglages franchise = new Reglages();
        franchise.regimeTva = Reglages.FRANCHISE;
        Finance fr = new Finance(Chantier.replay("c1", ev), franchise);
        assertEquals(4590L, (long) fr.reelParPoste().get(Postes.MATERIAUX)); // en franchise, le coût est le TTC
        assertEquals(0, fr.tvaDeductible());
        assertEquals("TVA non applicable, art. 293 B du CGI.", RecapFin.construire(fr, false).mentionTva);
    }

    @Test
    public void parDefaut_coutsEnTtc_budgetEngageDisponibleAlertes() {
        add("c", Event.CHANTIER_CREE, "titre", "Cuisine", "mode", "forfait", "tva_defaut_bp", "1000", "avancement_bp", "5000");
        List<Ligne> l = Arrays.asList(
                ligne("Meubles", "1", 300000, 1000, 200000, Postes.MATERIAUX),
                ligne("Plomberie", "1", 80000, 1000, 50000, Postes.SOUS_TRAITANCE));
        add("d1", Event.DEVIS, "sorte", "nous", "statut", "signe", "lignes", Ligne.encoder(l));
        // Sous-traitant accepté 500 € TTC, déjà facturé 200 € TTC.
        add("st", Event.DEVIS, "sorte", "sous_traitant", "statut", "signe",
                "lignes", Ligne.encoder(Arrays.asList(ligne("Plomberie", "1", 41667, 2000, 0, Postes.SOUS_TRAITANCE))));
        add("e1", Event.DEPENSE, "montant_centimes", "170000", "tva_bp", "2000", "categorie", "materiel");   // 1 700 € TTC
        add("e2", Event.DEPENSE, "montant_centimes", "20000", "tva_bp", "2000", "categorie", "sous_traitance");

        Finance f = new Finance(Chantier.replay("c1", ev), new Reglages());   // défaut : TVA non récupérée
        assertEquals(170000L, (long) f.depenseParPoste().get(Postes.MATERIAUX));   // compté en TTC
        assertEquals(30000L, (long) f.engageParPoste().get(Postes.SOUS_TRAITANCE)); // 500 − 200
        assertEquals(50000L, (long) f.reelParPoste().get(Postes.SOUS_TRAITANCE));
        assertEquals(30000, f.disponible(Postes.MATERIAUX));
        assertEquals(1, f.alerte(Postes.MATERIAUX));             // 85 % du budget
        assertEquals(1, f.alerte(Postes.SOUS_TRAITANCE));        // 100 % pile
        assertEquals(8800, f.consommeBp());                      // 2 200 / 2 500
        assertEquals(250000, f.previsionDeboursSec());
        assertEquals(5000, f.c.avancementBp);
        assertEquals(0, f.tvaDeductible());
        assertEquals(380000, f.venduHt());                       // CA hors TVA facturée
    }

    @Test
    public void entreProches_decompteSansTva() {
        add("c", Event.CHANTIER_CREE, "titre", "Terrasse du neveu", "type", "proches", "taux_convenu_centimes", "2000");
        add("e1", Event.DEPENSE, "montant_centimes", "45900", "tva_bp", "2000", "fournisseur", "Leroy Merlin", "categorie", "materiel");
        add("e2", Event.DEPENSE, "montant_centimes", "6000", "categorie", "carburant");
        add("h1", Event.HEURES, "heures_centiemes", "750", "date", "18/10/2026");
        add("h2", Event.HEURES, "heures_centiemes", "800");
        add("v1", Event.FACTURATION, "libelle", "Versement", "ht_centimes", "30000", "statut", "encaisse");

        Finance f = new Finance(Chantier.replay("c1", ev), new Reglages());
        assertEquals(0, f.tvaDefaut());
        assertEquals(51900, f.materielRefactureHt());            // tickets TTC, tous remboursables
        assertEquals(31000, f.mainOeuvreDue());                  // 15,5 h × 20 €
        assertEquals(82900, f.venduHt());
        assertEquals(30000, f.encaisse());
        assertEquals(0, f.fraisGeneraux(100000));

        RecapFin r = RecapFin.construire(f, true);
        assertEquals(82900, r.totalTtc);
        assertEquals(0, r.totalTva);
        assertEquals(52900, r.resteAPayer());
        assertEquals("", r.mentionTva);
    }

    @Test
    public void heures_saisieEtAffichage() {
        assertEquals(750, Heures.parse("7,5"));
        assertEquals(750, Heures.parse("7h30"));
        assertEquals(825, Heures.parse("8:15"));
        assertEquals(800, Heures.parse("8"));
        assertEquals(-1, Heures.parse("8h75"));
        assertEquals("7 h 30", Heures.format(750));
        assertEquals("8 h", Heures.format(800));
        assertEquals("7,5", Heures.toInput(750));
    }

    @Test
    public void prixConseille() {
        Reglages r = new Reglages();
        r.fraisGenerauxBp = 3300;
        r.margeViseeBp = 3000;
        assertEquals(1729, r.prixConseille(1000));               // 10 € × 1,33 × 1,30 = 17,29 €
    }
}
