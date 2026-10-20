package com.gregory.truelle.data;

/** Catégories de dépenses. La catégorie est devinée depuis le fournisseur, et reste modifiable. */
public final class Categories {

    public static final String MATERIEL = "materiel";
    public static final String LOCATION = "location";
    public static final String CARBURANT = "carburant";
    public static final String REPAS = "repas";
    public static final String DIVERS = "divers";
    /** Facture d'un sous-traitant. */
    public static final String SOUS_TRAITANCE = "sous_traitance";

    /** {clé, libellé}, dans l'ordre d'affichage. */
    public static final String[][] TOUTES = {
            {MATERIEL, "Matériel"},
            {LOCATION, "Outillage et location"},
            {CARBURANT, "Carburant et déplacements"},
            {REPAS, "Repas"},
            {SOUS_TRAITANCE, "Sous-traitance"},
            {DIVERS, "Divers"},
    };

    private static final String[] LOUEURS = {"KILOUTOU", "LOXAM", "HERTZ", "FRAIKIN", "SALTI", "LOCATION", "LOC'", "WURTH", "HILTI", "MAKITA"};
    private static final String[] STATIONS = {"TOTAL", "ESSO", "SHELL", "BP ", "AVIA", "STATION", "CARBURANT", "AUTOROUTE", "VINCI", "SANEF", "APRR", "PEAGE", "PARKING"};
    private static final String[] RESTOS = {"RESTAURANT", "BRASSERIE", "BOULANGERIE", "MCDONALD", "BURGER", "KEBAB", "PIZZ", "CAFE", "SNACK", "TRAITEUR"};

    private Categories() {}

    public static String libelle(String cle) {
        for (String[] c : TOUTES) if (c[0].equals(cle)) return c[1];
        return "Matériel";
    }

    /** Ancienne dépense sans catégorie, ou valeur inconnue : matériel. */
    public static String normaliser(String cle) {
        for (String[] c : TOUTES) if (c[0].equals(cle)) return cle;
        return MATERIEL;
    }

    public static String deviner(String fournisseur) {
        if (fournisseur == null || fournisseur.trim().isEmpty()) return MATERIEL;
        String f = " " + ReceiptParser.normaliser(fournisseur) + " ";
        for (String s : STATIONS) if (f.contains(s)) return CARBURANT;
        for (String s : LOUEURS) if (f.contains(s)) return LOCATION;
        for (String s : RESTOS) if (f.contains(s)) return REPAS;
        return MATERIEL;
    }
}
