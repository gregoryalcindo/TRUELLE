package com.gregory.truelle.data;

/** Postes de coût du déboursé sec d'un chantier. */
public final class Postes {

    public static final String MATERIAUX = "materiaux";
    public static final String MAIN_OEUVRE = "main_oeuvre";
    public static final String SOUS_TRAITANCE = "sous_traitance";
    public static final String MATERIEL = "materiel_location";
    public static final String FRAIS_CHANTIER = "frais_chantier";

    public static final String[][] TOUS = {
            {MATERIAUX, "Matériaux et fournitures"},
            {MAIN_OEUVRE, "Main d'œuvre"},
            {SOUS_TRAITANCE, "Sous-traitance"},
            {MATERIEL, "Matériel et location"},
            {FRAIS_CHANTIER, "Frais de chantier"},
    };

    /** Libellés courts pour les boutons. */
    public static final String[][] COURTS = {
            {MATERIAUX, "Matériaux"}, {MAIN_OEUVRE, "Main d'œuvre"}, {SOUS_TRAITANCE, "Sous-traitance"},
            {MATERIEL, "Matériel"}, {FRAIS_CHANTIER, "Frais"},
    };

    private Postes() {}

    public static String libelle(String cle) {
        for (String[] p : TOUS) if (p[0].equals(cle)) return p[1];
        return "Matériaux et fournitures";
    }

    public static String normaliser(String cle) {
        for (String[] p : TOUS) if (p[0].equals(cle)) return cle;
        return MATERIAUX;
    }

    /** Poste d'une dépense selon sa catégorie. */
    public static String deCategorie(String categorie) {
        switch (Categories.normaliser(categorie)) {
            case Categories.LOCATION: return MATERIEL;
            case Categories.SOUS_TRAITANCE: return SOUS_TRAITANCE;
            case Categories.CARBURANT:
            case Categories.REPAS:
            case Categories.DIVERS: return FRAIS_CHANTIER;
            default: return MATERIAUX;
        }
    }
}
