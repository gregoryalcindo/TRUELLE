package com.gregory.truelle.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Le Carnet (tableau de bord de toute l'activité), calculé sur tous les chantiers :
 * ce qu'il y a à faire aujourd'hui, puis l'argent.
 */
public final class Carnet {

    /** Au-delà de ce délai, une facture non payée est « en retard ». */
    public static final int JOURS_RETARD = 15;

    public static final int ATTENTION = 1;
    public static final int PROBLEME = 2;

    public static final class Tache {
        public final Chantier chantier;
        public final Chantier.Element tache;

        Tache(Chantier chantier, Chantier.Element tache) {
            this.chantier = chantier;
            this.tache = tache;
        }
    }

    public static final class Alerte {
        public final Chantier chantier;
        /** ATTENTION (ambre) ou PROBLEME (rouge). */
        public final int niveau;
        public final String titre;
        public final String detail;
        /** Ligne de facturation concernée (relance), sinon null. */
        public final Chantier.Element facture;
        /** Pour une facture en retard : le net à payer. */
        public final long montant;

        Alerte(Chantier chantier, int niveau, String titre, String detail, Chantier.Element facture, long montant) {
            this.chantier = chantier;
            this.niveau = niveau;
            this.titre = titre;
            this.detail = detail;
            this.facture = facture;
            this.montant = montant;
        }
    }

    public final List<Tache> taches = new ArrayList<>();
    public final List<Alerte> alertes = new ArrayList<>();
    public final List<Chantier> enCours = new ArrayList<>();
    public long aEncaisser;
    public long enRetard;
    public int facturesEnAttente;
    public long encaisseMois;
    public long encaisseMoisPrecedent;
    public long beneficeEstime;
    /** Chantiers en cours dont le bénéfice est estimable (un budget existe). */
    public int chantiersEstimes;

    public static Carnet construire(List<Chantier> chantiers, Reglages r) {
        Carnet k = new Carnet();
        LocalDate auj = Dates.aujourdhuiDate();
        LocalDate moisPrec = auj.minusMonths(1);
        for (Chantier c : chantiers) {
            Finance f = new Finance(c, r);
            boolean termine = Chantier.TERMINE.equals(c.statut);

            // Tâches du jour ou en retard, pas encore faites.
            for (Chantier.Element t : c.taches()) {
                if (Chantier.tacheFaite(t)) continue;
                LocalDate d = Dates.parse(t.get("date"));
                if (d != null && !d.isAfter(auj)) k.taches.add(new Tache(c, t));
            }

            // Dépenses à vérifier.
            int n = f.aVerifier();
            if (n > 0) {
                k.alertes.add(new Alerte(c, ATTENTION, n + (n > 1 ? " tickets à vérifier" : " ticket à vérifier"),
                        c.titre, null, 0));
            }

            // Budget dépassé (sur un chantier pas encore terminé).
            if (!termine) {
                for (String[] p : Postes.TOUS) {
                    if (f.alerte(p[0]) == 2) {
                        long depassement = -f.disponible(p[0]);
                        k.alertes.add(new Alerte(c, PROBLEME, p[1] + " : budget dépassé",
                                c.titre + " · +" + Money.format(depassement) + " au-dessus du prévu", null, depassement));
                    }
                }
            }

            // Argent : à encaisser, retards, encaissé ce mois.
            for (Chantier.Element el : c.elements(Event.FACTURATION)) {
                String statut = el.get("statut");
                if (Chantier.FACT_FACTURE.equals(statut)) {
                    long net = f.netAPayer(el);
                    k.aEncaisser += net;
                    k.facturesEnAttente++;
                    long jours = Dates.joursDepuis(el.get("date_facture"));
                    if (jours >= JOURS_RETARD) {
                        k.enRetard += net;
                        String lib = el.get("libelle").isEmpty() ? "Facture" : el.get("libelle");
                        k.alertes.add(new Alerte(c, PROBLEME, lib + " : pas payé depuis " + jours + " jours",
                                c.titre + " · " + Money.format(net), el, net));
                    }
                } else if (Chantier.FACT_ENCAISSE.equals(statut)) {
                    String d = el.get("date_encaisse");
                    if (Dates.dansMois(d, auj.getYear(), auj.getMonthValue())) k.encaisseMois += f.netAPayer(el);
                    else if (Dates.dansMois(d, moisPrec.getYear(), moisPrec.getMonthValue())) k.encaisseMoisPrecedent += f.netAPayer(el);
                }
            }

            if (Chantier.EN_COURS.equals(c.statut)) {
                k.enCours.add(c);
                // Sans budget, le bénéfice n'est pas estimable : on ne l'additionne pas.
                if (f.consommeBp() >= 0) {
                    k.beneficeEstime += f.prevision().benefice;
                    k.chantiersEstimes++;
                }
            }
        }
        // Les problèmes avant les points d'attention ; les tâches en retard d'abord.
        k.alertes.sort((x, y) -> Integer.compare(y.niveau, x.niveau));
        k.taches.sort((x, y) -> Long.compare(Dates.cle(x.tache.get("date")), Dates.cle(y.tache.get("date"))));
        return k;
    }

    public int nombreAFaire() {
        return taches.size() + alertes.size();
    }

    /** Évolution de l'encaissé par rapport au mois précédent, en %, ou null si pas comparable. */
    public Long evolutionPct() {
        if (encaisseMoisPrecedent <= 0) return null;
        return Math.round((encaisseMois - encaisseMoisPrecedent) * 100.0 / encaisseMoisPrecedent);
    }
}
