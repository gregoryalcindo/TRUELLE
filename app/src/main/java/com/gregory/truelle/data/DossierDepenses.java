package com.gregory.truelle.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenu du « Dossier des factures de dépenses » : toutes les dépenses du chantier,
 * rangées par catégorie, avec leurs factures en annexe (refacturées ou non).
 * Document interne (pour toi ou le comptable), distinct du récapitulatif client.
 */
public final class DossierDepenses {

    public final List<RecapFin.Section> sections = new ArrayList<>();
    public final List<RecapFin.Annexe> annexes = new ArrayList<>();
    public long total;
    public int sansFacture;

    public static DossierDepenses construire(Chantier c) {
        DossierDepenses d = new DossierDepenses();
        int n = 0;
        for (String[] cat : Categories.TOUTES) {
            RecapFin.Section s = new RecapFin.Section(cat[1]);
            List<Chantier.Depense> liste = c.depenses();
            // Par date de saisie, de la plus ancienne à la plus récente.
            liste.sort((x, y) -> Long.compare(x.creeLe, y.creeLe));
            for (Chantier.Depense dep : liste) {
                if (!dep.categorie.equals(cat[0])) continue;
                String nom = dep.fournisseur.isEmpty() ? "Dépense" : dep.fournisseur;
                StringBuilder detail = new StringBuilder(dep.date);
                detail.append(detail.length() > 0 ? " · " : "").append("HT ").append(Money.format(Finance.htDepense(dep)))
                        .append(" · TVA ").append(Tva.libelle(Finance.tvaDepense(dep)))
                        .append(" · ").append(c.estARefacturer(dep) ? "refacturée" : "interne");
                if (!dep.valide) detail.append(" · à vérifier");
                if (!dep.fichier.isEmpty()) {
                    n++;
                    detail.append(" · facture ").append(n);
                    d.annexes.add(new RecapFin.Annexe("Facture " + n + " — " + nom + (dep.date.isEmpty() ? "" : " du " + dep.date)
                            + " — " + cat[1], dep.fichier));
                } else {
                    detail.append(" · sans facture");
                    d.sansFacture++;
                }
                s.ajouter(nom, detail.toString(), dep.montantCentimes);
            }
            if (!s.lignes.isEmpty()) {
                d.sections.add(s);
                d.total += s.total;
            }
        }
        return d;
    }
}
