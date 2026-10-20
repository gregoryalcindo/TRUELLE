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
    /** Montants HT des sections ; TVA par taux ; TTC. */
    public long totalHt;
    public final java.util.Map<Integer, Long> tvaParTaux = new java.util.TreeMap<>();
    public long totalTva;
    public long totalTtc;
    /** Déjà réglé par le client (encaissé) et retenues de garantie conservées. */
    public long dejaRegle;
    public long retenues;
    /** Mention légale de TVA à imprimer (franchise), vide sinon. */
    public String mentionTva = "";
    /** Mentions de certification (taux réduits) à reporter sur la vraie facture. */
    public final List<String> mentionsFacture = new ArrayList<>();

    public long resteAPayer() {
        return totalTtc - dejaRegle - retenues;
    }

    public static RecapFin construire(Finance f, boolean joindreFactures) {
        Chantier c = f.c;
        RecapFin r = new RecapFin();

        boolean sansTva = f.sansTvaFacturee();
        String suffixe = sansTva ? "" : " (HT)";
        Section devis = new Section("Devis signés" + suffixe);
        for (Chantier.Element el : c.elements(Event.DEVIS)) {
            if (!Finance.estNotreDevis(el) || !Finance.signe(el)) continue;
            Finance.Totaux t = f.totaux(el);
            StringBuilder detail = new StringBuilder();
            for (java.util.Map.Entry<Integer, Long> e : t.tvaParTaux.entrySet()) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append("TVA ").append(Tva.libelle(e.getKey())).append(" : ").append(Money.format(e.getValue()));
            }
            if (t.remise > 0) detail.append(detail.length() > 0 ? " · " : "").append("remise ").append(Money.format(t.remise));
            devis.ajouter(el.get("libelle").isEmpty() ? "Devis" : el.get("libelle"), detail.toString(), t.ht);
            for (java.util.Map.Entry<Integer, Long> e : t.tvaParTaux.entrySet()) r.tvaParTaux.merge(e.getKey(), e.getValue(), Long::sum);
        }
        if (!devis.lignes.isEmpty()) r.sections.add(devis);

        Section travaux = new Section("Travaux supplémentaires acceptés" + suffixe);
        for (Chantier.Element el : c.elements(Event.TRAVAUX)) {
            if (!Finance.accepte(el)) continue;
            int bp = sansTva ? 0 : f.tvaTravaux(el);
            long ht = f.htTravaux(el);
            String detail = (sansTva ? "" : "TVA " + Tva.libelle(bp)) + (el.get("signe_le").isEmpty() ? "" : (sansTva ? "" : " · ") + "accord signé le " + el.get("signe_le"));
            travaux.ajouter(el.get("description"), detail, ht);
            r.tvaParTaux.merge(bp, Tva.tva(ht, bp), Long::sum);
            if (!el.get("signature").isEmpty()) r.signatures.add(new String[]{el.get("description"), el.get("signe_le"), el.get("signature")});
        }
        if (!travaux.lignes.isEmpty()) r.sections.add(travaux);

        int bpMat = f.tvaDefaut();
        Section materiel = new Section((c.majorationPct > 0 ? "Matériel refacturé (prix d'achat + " + c.majorationPct + " %)" : "Matériel refacturé au prix d'achat") + suffixe);
        int n = 0;
        for (Chantier.Depense d : c.depenses()) {
            if (!c.estARefacturer(d)) continue;
            String nom = d.fournisseur.isEmpty() ? "Achat" : d.fournisseur;
            String detail = (d.date.isEmpty() ? "" : d.date) + (c.majorationPct > 0 ? (d.date.isEmpty() ? "" : " · ") + "achat " + Money.format(f.coutDepense(d)) : "");
            if (joindreFactures && !d.fichier.isEmpty()) {
                n++;
                detail += (detail.isEmpty() ? "" : " · ") + "voir annexe " + n;
                r.annexes.add(new Annexe("Annexe " + n + " — " + nom + (d.date.isEmpty() ? "" : " du " + d.date), d.fichier));
            }
            long ht = f.refactureHt(d);
            materiel.ajouter(nom, detail, ht);
            r.tvaParTaux.merge(bpMat, Tva.tva(ht, bpMat), Long::sum);
        }
        if (!materiel.lignes.isEmpty()) r.sections.add(materiel);

        for (Section s : r.sections) r.totalHt += s.total;
        r.tvaParTaux.entrySet().removeIf(e -> e.getValue() == 0 && e.getKey() != 0);
        for (long v : r.tvaParTaux.values()) r.totalTva += v;
        r.totalTtc = r.totalHt + r.totalTva;
        r.dejaRegle = f.encaisse();
        r.retenues = f.retenues();
        r.mentionTva = f.mentionTva();
        // Taux réduits du bâtiment : la mention de certification à reporter sur la facture.
        if (r.tvaParTaux.containsKey(Tva.REDUIT) && r.tvaParTaux.get(Tva.REDUIT) > 0) r.mentionsFacture.add(TvaGuide.MENTION_FACTURE_55);
        if (r.tvaParTaux.containsKey(Tva.INTERMEDIAIRE) && r.tvaParTaux.get(Tva.INTERMEDIAIRE) > 0) r.mentionsFacture.add(TvaGuide.MENTION_FACTURE_10);
        return r;
    }
}
