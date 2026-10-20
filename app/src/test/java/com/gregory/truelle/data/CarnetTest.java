package com.gregory.truelle.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Tâches, photos, dates et Carnet (tableau de bord), relus depuis le journal. */
public class CarnetTest {

    private long t = 0;

    private Event ev(String id, String type, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return new Event(id, type, "c1", ++t, "Papa", m);
    }

    @Test
    public void dates_formatTriEtRelatif() {
        Dates.aujourdhuiFixe = LocalDate.of(2026, 10, 20); // un mardi
        try {
            assertEquals("20/10/2026", Dates.aujourdhui());
            assertEquals("Aujourd'hui", Dates.relatif("20/10/2026"));
            assertEquals("Demain", Dates.relatif("21/10/2026"));
            assertEquals("Hier", Dates.relatif("19/10/2026"));
            assertEquals(LocalDate.of(2026, 10, 19), Dates.lundi(Dates.aujourdhuiDate()));
            assertTrue(Dates.passee("19/10/2026"));
            assertFalse(Dates.passee("20/10/2026"));
            assertEquals(15, Dates.joursDepuis("05/10/2026"));
            assertEquals(Long.MAX_VALUE, Dates.cle(""));
            assertNull(Dates.parse("pas une date"));
            assertTrue(Dates.cle("01/11/2026") > Dates.cle("31/10/2026"));
            assertEquals("mardi 20 octobre", Dates.long_(Dates.aujourdhuiDate()));
        } finally {
            Dates.aujourdhuiFixe = null;
        }
    }

    @Test
    public void taches_ordreCocheEtSuppression() {
        List<Event> e = new ArrayList<>(Arrays.asList(
                ev("c", Event.CHANTIER_CREE, "titre", "Salle de bain"),
                ev("t1", Event.TACHE, "texte", "Joints", "date", "23/10/2026"),
                ev("t2", Event.TACHE, "texte", "Faïence", "date", "20/10/2026", "qui", "Papa"),
                ev("t3", Event.TACHE, "texte", "Acheter silicone"),
                ev("t4", Event.TACHE, "texte", "Dépose baignoire", "date", "15/10/2026")));
        e.add(ev("x", Event.CORRECTION, "cible", "t4", "fait", "oui", "fait_le", "16/10/2026"));
        Chantier c = Chantier.replay("c1", e);
        List<Chantier.Element> l = c.taches();
        assertEquals(4, l.size());
        assertEquals("Faïence", l.get(0).get("texte"));      // la plus proche d'abord
        assertEquals("Joints", l.get(1).get("texte"));
        assertEquals("Acheter silicone", l.get(2).get("texte")); // sans date : après
        assertEquals("Dépose baignoire", l.get(3).get("texte")); // faite : à la fin
        assertEquals(1, c.tachesFaites());

        e.add(ev("y", Event.SUPPRESSION, "cible", "t3"));
        e.add(ev("z", Event.CORRECTION, "cible", "t4", "fait", "non"));
        Chantier c2 = Chantier.replay("c1", e);
        assertEquals(3, c2.taches().size());
        assertEquals(0, c2.tachesFaites());
    }

    @Test
    public void photos_couvertureEtMoment() {
        List<Event> e = new ArrayList<>(Arrays.asList(
                ev("c", Event.CHANTIER_CREE, "titre", "Cuisine", "telephone", "06 12 34 56 78"),
                ev("p1", Event.PHOTO, "fichier", "Photos/a.jpg", "miniature", "Photos/Miniatures/a.jpg", "moment", "avant"),
                ev("p2", Event.PHOTO, "fichier", "Photos/b.jpg", "miniature", "Photos/Miniatures/b.jpg", "moment", "pendant")));
        Chantier c = Chantier.replay("c1", e);
        assertEquals("06 12 34 56 78", c.telephone);
        assertEquals("p2", c.photoCouverture().id);              // la plus récente par défaut
        assertEquals(Chantier.MOMENT_AVANT, c.momentParDefaut()); // chantier à démarrer
        e.add(ev("m", Event.CHANTIER_MODIFIE, "couverture", "p1"));
        e.add(ev("s", Event.STATUT, "valeur", Chantier.TERMINE));
        Chantier c2 = Chantier.replay("c1", e);
        assertEquals("p1", c2.photoCouverture().id);
        assertEquals(Chantier.MOMENT_APRES, c2.momentParDefaut());
        e.add(ev("d", Event.SUPPRESSION, "cible", "p1"));
        assertEquals("p2", Chantier.replay("c1", e).photoCouverture().id); // couverture retirée : on retombe sur la dernière
    }

    @Test
    public void carnet_journeeAlertesEtArgent() {
        Dates.aujourdhuiFixe = LocalDate.of(2026, 10, 20);
        try {
            List<Event> e = Arrays.asList(
                    ev("c", Event.CHANTIER_CREE, "titre", "Salle de bain", "tva_defaut_bp", "0"),
                    ev("s", Event.STATUT, "valeur", Chantier.EN_COURS),
                    ev("t1", Event.TACHE, "texte", "Aujourd'hui", "date", "20/10/2026"),
                    ev("t2", Event.TACHE, "texte", "En retard", "date", "18/10/2026"),
                    ev("t3", Event.TACHE, "texte", "Plus tard", "date", "25/10/2026"),
                    ev("t4", Event.TACHE, "texte", "Faite", "date", "19/10/2026", "fait", "oui"),
                    ev("d1", Event.DEPENSE, "montant_centimes", "4590", "valide", "non"),
                    ev("f1", Event.FACTURATION, "libelle", "Acompte", "montant_centimes", "195000", "tva_bp", "0",
                            "statut", "facture", "date_facture", "01/10/2026"),
                    ev("f2", Event.FACTURATION, "libelle", "Situation n°1", "montant_centimes", "100000", "tva_bp", "0",
                            "statut", "facture", "date_facture", "15/10/2026"),
                    ev("f3", Event.FACTURATION, "libelle", "Solde ancien", "montant_centimes", "50000", "tva_bp", "0",
                            "statut", "encaisse", "date_encaisse", "12/10/2026"),
                    ev("f4", Event.FACTURATION, "libelle", "Mois dernier", "montant_centimes", "40000", "tva_bp", "0",
                            "statut", "encaisse", "date_encaisse", "28/09/2026"));
            Chantier c = Chantier.replay("c1", e);
            Carnet k = Carnet.construire(Arrays.asList(c), new Reglages());
            assertEquals(2, k.taches.size());
            assertEquals("En retard", k.taches.get(0).tache.get("texte"));
            assertEquals(295000, k.aEncaisser);
            assertEquals(195000, k.enRetard);                        // facturé il y a 19 jours
            assertEquals(2, k.facturesEnAttente);
            assertEquals(50000, k.encaisseMois);
            assertEquals(40000, k.encaisseMoisPrecedent);
            assertEquals(Long.valueOf(25), k.evolutionPct());
            assertEquals(2, k.alertes.size());
            assertEquals(Carnet.PROBLEME, k.alertes.get(0).niveau);  // le retard d'abord
            assertEquals("f1", k.alertes.get(0).facture.id);
            assertEquals(Carnet.ATTENTION, k.alertes.get(1).niveau); // 1 ticket à vérifier
            assertEquals(1, k.enCours.size());
            assertEquals(4, k.nombreAFaire());
        } finally {
            Dates.aujourdhuiFixe = null;
        }
    }

    @Test
    public void carnet_chantierTermineAvecResteAFacturer() {
        List<Ligne> l = Arrays.asList(new Ligne());
        l.get(0).designation = "Mur";
        l.get(0).quantiteMilli = 1000;
        l.get(0).puHtCentimes = 508500;
        l.get(0).tvaBp = 0;
        List<Event> e = Arrays.asList(
                ev("c", Event.CHANTIER_CREE, "titre", "Mur ext", "tva_defaut_bp", "0"),
                ev("d", Event.DEVIS, "sorte", "nous", "statut", "signe", "lignes", Ligne.encoder(l)),
                ev("f", Event.FACTURATION, "libelle", "Acompte", "montant_centimes", "150000", "tva_bp", "0",
                        "statut", "encaisse", "date_encaisse", "10/10/2026"),
                ev("s", Event.STATUT, "valeur", Chantier.TERMINE));
        Carnet k = Carnet.construire(Arrays.asList(Chantier.replay("c1", e)), new Reglages());
        assertEquals(358500, k.resteAFacturer);
        assertEquals(1, k.alertes.size());
        assertEquals(Carnet.ATTENTION, k.alertes.get(0).niveau);
        assertTrue(k.alertes.get(0).titre.contains("à facturer"));
    }
}
