package com.gregory.truelle.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Noms de fichiers lisibles et cohérents, sur le modèle « date - quoi - montant » :
 *   Dépenses/              2026-09-22 - Le Holloco - 10,85 €.pdf
 *   Devis/                 Devis - Devis initial - 5 000,00 €.pdf
 *                          Devis sous-traitant - Plomberie Martin - 1 200,00 €.pdf
 *   Travaux en plus/       2026-10-15 - Accord signé - Faïence plafond.png
 *   PDF client/            2026-005 - Récapitulatif fin de chantier - Salle de bain - 2026-10-18.pdf
 *   Factures des dépenses/ 2026-005 - Factures des dépenses - Salle de bain - 2026-10-18.pdf
 * Les dates sont écrites année-mois-jour pour que les fichiers se classent dans l'ordre.
 */
public final class NomsFichiers {

    private static final Pattern NUMERO = Pattern.compile("^(\\d{4}-\\d{3})");

    private NomsFichiers() {}

    public static String depense(String fournisseur, String dateJJMMAAAA, long montantCentimes) {
        return dateTri(dateJJMMAAAA) + " - " + (vide(fournisseur) ? "Dépense" : fournisseur.trim()) + " - " + montant(montantCentimes);
    }

    public static String devis(boolean nous, String libelle, String prestataire, long montantCentimes) {
        String quoi = nous ? (vide(libelle) ? "Devis" : libelle.trim())
                : (vide(prestataire) ? "" : prestataire.trim()) + (vide(libelle) ? "" : (vide(prestataire) ? "" : " ") + libelle.trim());
        return (nous ? "Devis - " : "Devis sous-traitant - ") + (quoi.isEmpty() ? "Sans titre" : quoi) + " - " + montant(montantCentimes);
    }

    public static String signature(String description) {
        return aujourdhui() + " - Accord signé - " + (vide(description) ? "Travaux en plus" : description.trim());
    }

    public static String pdfRecapitulatif(Chantier c) {
        return numero(c) + "Récapitulatif fin de chantier - " + c.titre + " - " + aujourdhui();
    }

    public static String pdfFactures(Chantier c) {
        return numero(c) + "Factures des dépenses - " + c.titre + " - " + aujourdhui();
    }

    /** Montant pour un nom de fichier : « 1 200,00 € » avec des espaces normales. */
    static String montant(long centimes) {
        return Money.format(centimes).replace(' ', ' ').replace(' ', ' ');
    }

    /** « 22/09/2026 » → « 2026-09-22 » ; date absente ou illisible → aujourd'hui. */
    static String dateTri(String jjmmaaaa) {
        if (jjmmaaaa != null) {
            Matcher m = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matcher(jjmmaaaa.trim());
            if (m.find()) return String.format(Locale.FRANCE, "%s-%02d-%02d", m.group(3), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        return aujourdhui();
    }

    private static String numero(Chantier c) {
        Matcher m = NUMERO.matcher(c.dossier == null ? "" : c.dossier);
        return m.find() ? m.group(1) + " - " : "";
    }

    private static String aujourdhui() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(new Date());
    }

    private static boolean vide(String s) {
        return s == null || s.trim().isEmpty();
    }
}
