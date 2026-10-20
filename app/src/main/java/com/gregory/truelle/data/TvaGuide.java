package com.gregory.truelle.data;

/**
 * Guide « Quel taux de TVA ? » pour les travaux du bâtiment, vérifié par Karim
 * (CGI art. 278, 278-0 bis A, 279-0 bis, 283-2 nonies, 293 B ; BOI-TVA-LIQ-30-20-90-40 et BOI-LETTRE-000280,
 * copie du corpus du 8 octobre 2026). France métropolitaine et Corse ; outre-mer : autres taux.
 *
 * Cinq questions, dans l'ordre, la première réponse « oui » qui tranche donne le taux.
 */
public final class TvaGuide {

    private TvaGuide() {}

    /** Les réponses du questionnaire (null = pas encore répondu). */
    public static final class Reponses {
        /** 1. Je travaille pour une autre entreprise du bâtiment (je suis son sous-traitant) ? */
        public Boolean sousTraitant;
        /** 2. Construction neuve, agrandissement de plus de 10 % ou remise à neuf du bâtiment ? */
        public Boolean neufOuAgrandissement;
        /** 3. Logement (habitation) achevé depuis plus de 2 ans ? */
        public Boolean logementPlusDe2Ans;
        /** 4. Rénovation énergétique (isolation, chauffage ou ventilation performants, eau chaude) ? */
        public Boolean renovationEnergetique;
        /** 5. Chaudière fioul ou gaz, nettoyage, jardin ou espaces verts ? */
        public Boolean exclu;
    }

    public static final class Resultat {
        /** Taux en points de base (2000 = 20 %) ; 0 avec autoliquidation. */
        public final int tauxBp;
        public final boolean autoliquidation;
        public final String titre;
        public final String pourquoi;
        /** Ce que le client doit certifier (taux réduit), sinon "". */
        public final String aFaireSigner;

        Resultat(int tauxBp, boolean autoliquidation, String titre, String pourquoi, String aFaireSigner) {
            this.tauxBp = tauxBp;
            this.autoliquidation = autoliquidation;
            this.titre = titre;
            this.pourquoi = pourquoi;
            this.aFaireSigner = aFaireSigner;
        }
    }

    /** Numéro de la prochaine question (1 à 5), ou 0 si le taux est trouvé. */
    public static int prochaineQuestion(Reponses r) {
        if (r.sousTraitant == null) return 1;
        if (r.sousTraitant) return 0;
        if (r.neufOuAgrandissement == null) return 2;
        if (r.neufOuAgrandissement) return 0;
        if (r.logementPlusDe2Ans == null) return 3;
        if (!r.logementPlusDe2Ans) return 0;
        if (r.exclu == null) return 4;
        if (r.exclu) return 0;
        if (r.renovationEnergetique == null) return 5;
        return 0;
    }

    public static Resultat resultat(Reponses r) {
        if (Boolean.TRUE.equals(r.sousTraitant)) {
            return new Resultat(Tva.AUCUNE, true, "Autoliquidation : pas de TVA sur votre facture",
                    "Vous êtes sous-traitant d'une entreprise : c'est elle qui déclare la TVA. Votre facture est hors taxes, avec la mention « Autoliquidation ».", "");
        }
        if (Boolean.TRUE.equals(r.neufOuAgrandissement)) {
            return new Resultat(Tva.NORMAL, false, "20 %",
                    "Construction neuve, agrandissement de plus de 10 % de la surface, ou bâtiment remis à neuf : taux normal.", "");
        }
        if (Boolean.FALSE.equals(r.logementPlusDe2Ans)) {
            return new Resultat(Tva.NORMAL, false, "20 %",
                    "Local professionnel, commerce, ou logement de moins de 2 ans : taux normal.", "");
        }
        if (Boolean.TRUE.equals(r.exclu)) {
            return new Resultat(Tva.NORMAL, false, "20 %",
                    "Chaudière fioul ou gaz, nettoyage, jardin et espaces verts restent à 20 %, même dans un logement ancien.", "");
        }
        if (Boolean.TRUE.equals(r.renovationEnergetique)) {
            return new Resultat(Tva.REDUIT, false, "5,5 %",
                    "Rénovation énergétique d'un logement de plus de 2 ans. Le matériel doit atteindre les performances fixées par arrêté : vérifiez la fiche technique du produit.",
                    MENTION_DEVIS_55);
        }
        return new Resultat(Tva.INTERMEDIAIRE, false, "10 %",
                "Travaux d'amélioration, d'aménagement ou d'entretien d'un logement de plus de 2 ans (maçonnerie, carrelage, peinture, plomberie, électricité…). "
                        + "Attention : l'électroménager et les meubles fournis restent à 20 % (sur une ligne séparée) ; leur pose reste à 10 %.",
                MENTION_DEVIS_10);
    }

    public static String question(int n) {
        switch (n) {
            case 1: return "Vous travaillez pour une autre entreprise du bâtiment (vous êtes son sous-traitant) ?";
            case 2: return "Construction neuve, agrandissement de plus de 10 % de la surface, ou bâtiment remis à neuf ?";
            case 3: return "C'est un logement (maison, appartement) terminé depuis plus de 2 ans ?";
            case 4: return "Il s'agit d'une chaudière fioul ou gaz, de nettoyage, ou de jardin et espaces verts ?";
            case 5: return "Ce sont des travaux de rénovation énergétique : isolation, chauffage ou ventilation performants, eau chaude (pompe à chaleur, isolation des combles…) ?";
            default: return "";
        }
    }

    /** Exemples courants : {situation, taux}. */
    public static final String[][] EXEMPLES = {
            {"Carrelage d'une salle de bain, maison de 1990", "10 %"},
            {"Peinture et plâtrerie d'un appartement ancien", "10 %"},
            {"Ravalement de façade, maison de 20 ans", "10 %"},
            {"Électricité refaite dans un logement ancien", "10 %"},
            {"Isolation des combles ou des murs, logement ancien", "5,5 %"},
            {"Pompe à chaleur ou chauffe-eau thermodynamique performants", "5,5 %"},
            {"Remplacement d'une chaudière gaz ou fioul", "20 %"},
            {"Lave-vaisselle ou meubles fournis dans une cuisine rénovée", "20 % (pose à 10 %)"},
            {"Extension de 30 m² sur une maison de 100 m²", "20 %"},
            {"Maison neuve", "20 %"},
            {"Travaux dans un logement de moins de 2 ans", "20 %"},
            {"Bureau, commerce ou local professionnel", "20 %"},
            {"Entretien de jardin, nettoyage", "20 %"},
            {"Chantier pour une entreprise du bâtiment (sous-traitance)", "Autoliquidation"},
            {"Travaux entre proches, non déclarés", "Pas de TVA"},
    };

    // --------------------------------------------------------------- mentions (BOI-LETTRE-000280)

    /** Taux à 10 % : à faire signer par le client sur le devis. */
    public static final String MENTION_DEVIS_10 = "Je soussigné(e) ………… certifie que les travaux réalisés concernent des locaux à usage d'habitation "
            + "achevés depuis plus de deux ans et qu'ils n'auront pas pour effet, sur une période de deux ans au plus, de concourir à la production "
            + "d'un immeuble neuf au sens du 2° du 2 du I de l'article 257 du CGI, ni d'entraîner une augmentation de la surface de plancher des "
            + "locaux existants supérieure à 10 %.";

    /** Taux à 5,5 % : à faire signer par le client sur le devis. */
    public static final String MENTION_DEVIS_55 = "Je soussigné(e) ………… (nom, prénoms) certifie, en qualité de preneur de la prestation, que les travaux "
            + "réalisés concernent des locaux à usage d'habitation achevés depuis plus de deux ans, qu'ils n'auront pas pour effet, sur une période "
            + "de deux ans au plus, de concourir à la production d'un immeuble neuf au sens du 2° du 2 du I de l'article 257 du CGI, ni d'entraîner "
            + "une augmentation de la surface de plancher des locaux existants supérieure à 10 % et qu'ils ont la nature de travaux de rénovation "
            + "énergétique.";

    /** Taux à 10 % : mention sur la facture (si le devis n'a pas été certifié). */
    public static final String MENTION_FACTURE_10 = "Par le règlement de la présente facture, le client certifie, en qualité de preneur de la prestation, "
            + "que les travaux réalisés concernent des locaux à usage d'habitation achevés depuis plus de deux ans, qu'ils n'ont pas eu pour effet, "
            + "sur une période de deux ans au plus, de concourir à la production d'un immeuble neuf au sens du 2° du 2 du I de l'article 257 du CGI, "
            + "ni d'entraîner une augmentation de la surface de plancher des locaux existants supérieure à 10 %.";

    /** Taux à 5,5 % : mention sur la facture. */
    public static final String MENTION_FACTURE_55 = MENTION_FACTURE_10.substring(0, MENTION_FACTURE_10.length() - 1)
            + " et qu'ils ont la nature de travaux de rénovation énergétique.";

    /** Franchise en base (CGI art. 293 B ; la référence au CGI reste admise jusqu'au 30 juin 2028 après le passage au CIBS). */
    public static final String MENTION_FRANCHISE = "TVA non applicable, art. 293 B du CGI.";

    /** Sous-traitance du bâtiment (CGI art. 283-2 nonies). */
    public static final String MENTION_AUTOLIQUIDATION = "Autoliquidation : TVA due par le preneur (art. 283-2 nonies du CGI).";

    /** En dessous de ce montant TTC, pour de la réparation ou de l'entretien, la certification n'est pas exigée (BOI-TVA-LIQ-30-20-90-40 § 90). */
    public static final long SEUIL_SANS_CERTIFICATION_TTC = 100000;

    /** Vrai si un taux réduit du bâtiment (5,5 % ou 10 %) exige la certification du client. */
    public static boolean tauxReduitBatiment(int bp) {
        return bp == Tva.REDUIT || bp == Tva.INTERMEDIAIRE;
    }
}
