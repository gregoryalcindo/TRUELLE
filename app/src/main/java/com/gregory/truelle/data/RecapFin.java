package com.gregory.truelle.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenu du PDF de fin de chantier, calculé à partir du chantier (sans dessin).
 * Séparé du rendu PDF pour pouvoir le tester sur ordinateur.
 */
public final class RecapFin {

    public static final class Ligne {
        public final String libelle;
        public final String detail;
        public final long montantCentimes;

        Ligne(String libelle, String detail, long montantCentimes) {
            this.libelle = libelle;
            this.detail = detail == null ? "" : detail;
            this.montantCentimes = montantCentimes;
        }
    }

    public static final class Section {
        public final String titre;
        public final List<Ligne> lignes = new ArrayList<>();
        public long total;

        Section(String titre) {
            this.titre = titre;
        }

        void ajouter(String libelle, String detail, long montant) {
            lignes.add(new Ligne(libelle, detail, montant));
            total += montant;
        }
    }

    /** Un document à mettre en annexe (facture fournisseur refacturée). */
    public static final class Annexe {
        public final String titre;
        public final String fichier; // chemin relatif dans le dossier du chantier

        Annexe(String titre, String fichier) {
            this.titre = titre;
            this.fichier = fichier;
        }
    }

    public final List<Section> sections = new ArrayList<>();
    public final List<Annexe> annexes = new ArrayList<>();
    /** Travaux en plus signés : {description, date, chemin de la signature}. */
    public final List<String[]> signatures = new ArrayList<>();
    public long totalTtc;
    public long dejaRegle;

    public long resteAPayer() {
        return totalTtc - dejaRegle;
    }

    public static RecapFin construire(Chantier c, boolean joindreFactures) {
        RecapFin r = new RecapFin();

        Section devis = new Section("Devis signés");
        for (Chantier.Element el : c.elements(Event.DEVIS)) {
            if (Chantier.DEVIS_NOUS.equals(el.get("sorte")) && Chantier.DEVIS_SIGNE.equals(el.get("statut"))) {
                devis.ajouter(el.get("libelle").isEmpty() ? "Devis" : el.get("libelle"), "", el.centimes("montant_centimes"));
            }
        }
        if (!devis.lignes.isEmpty()) r.sections.add(devis);

        Section travaux = new Section("Travaux supplémentaires acceptés");
        for (Chantier.Element el : c.elements(Event.TRAVAUX)) {
            String s = el.get("statut");
            if (!Chantier.TRAVAUX_ACCEPTE.equals(s) && !Chantier.TRAVAUX_FAIT.equals(s)) continue;
            String detail = el.get("signe_le").isEmpty() ? "" : "Accord signé le " + el.get("signe_le");
            travaux.ajouter(el.get("description"), detail, el.centimes("montant_centimes"));
            if (!el.get("signature").isEmpty()) r.signatures.add(new String[]{el.get("description"), el.get("signe_le"), el.get("signature")});
        }
        if (!travaux.lignes.isEmpty()) r.sections.add(travaux);

        Section materiel = new Section(c.majorationPct > 0 ? "Matériel refacturé (prix d'achat + " + c.majorationPct + " %)" : "Matériel refacturé au prix d'achat");
        int n = 0;
        for (Chantier.Depense d : c.depenses()) {
            if (!c.estARefacturer(d)) continue;
            String nom = d.fournisseur.isEmpty() ? "Achat" : d.fournisseur;
            String detail = (d.date.isEmpty() ? "" : d.date) + (c.majorationPct > 0 ? (d.date.isEmpty() ? "" : " · ") + "achat " + Money.format(d.montantCentimes) : "");
            if (joindreFactures && !d.fichier.isEmpty()) {
                n++;
                detail += (detail.isEmpty() ? "" : " · ") + "voir annexe " + n;
                r.annexes.add(new Annexe("Annexe " + n + " — " + nom + (d.date.isEmpty() ? "" : " du " + d.date), d.fichier));
            }
            materiel.ajouter(nom, detail, c.montantRefacture(d));
        }
        if (!materiel.lignes.isEmpty()) r.sections.add(materiel);

        for (Section s : r.sections) r.totalTtc += s.total;
        r.dejaRegle = c.totalEncaisse();
        return r;
    }
}
