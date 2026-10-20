package com.gregory.truelle.data;

import java.util.Map;

/** Réglages financiers de l'entreprise, lus depuis les infos de l'entreprise (_App/journal). */
public final class Reglages {

    public static final String ASSUJETTI = "assujetti";
    public static final String FRANCHISE = "franchise";

    /** Ventes : TVA facturée aux clients (assujetti) ou franchise en base (art. 293 B CGI). */
    public String regimeTva = ASSUJETTI;
    /** Achats : TVA récupérée ? Non par défaut : les coûts se comptent alors en TTC. */
    public boolean recupereTva = false;
    /** Coût horaire chargé d'une heure de main d'œuvre, 0 = non réglé. */
    public long tauxHoraireCentimes = 0;
    /** Frais généraux en % du déboursé sec (points de base). */
    public int fraisGenerauxBp = 0;
    /** Marge visée (points de base), pour l'aide au prix. */
    public int margeViseeBp = 0;
    public int tvaDefautBp = Tva.NORMAL;

    public static Reglages lire(Map<String, String> entreprise) {
        Reglages r = new Reglages();
        if (entreprise == null) return r;
        if (FRANCHISE.equals(entreprise.get("regime_tva"))) r.regimeTva = FRANCHISE;
        r.recupereTva = "oui".equals(entreprise.get("recupere_tva")) && !r.franchise();
        r.tauxHoraireCentimes = Chantier.parseLong(entreprise.get("taux_horaire_centimes"), 0);
        r.fraisGenerauxBp = (int) Chantier.parseLong(entreprise.get("frais_generaux_bp"), 0);
        r.margeViseeBp = (int) Chantier.parseLong(entreprise.get("marge_visee_bp"), 0);
        r.tvaDefautBp = (int) Chantier.parseLong(entreprise.get("tva_defaut_bp"), Tva.NORMAL);
        return r;
    }

    public boolean franchise() {
        return FRANCHISE.equals(regimeTva);
    }

    /** TVA à proposer sur ce chantier. En franchise : 0. */
    public int tvaPour(Chantier c) {
        if (franchise()) return Tva.AUCUNE;
        return c != null && c.tvaDefautBp >= 0 ? c.tvaDefautBp : tvaDefautBp;
    }

    /** Prix de vente HT conseillé : coût × (1 + frais généraux) × (1 + marge visée). */
    public long prixConseille(long coutCentimes) {
        long avecFg = coutCentimes + Tva.arrondi(coutCentimes * (long) fraisGenerauxBp, 10000);
        return avecFg + Tva.arrondi(avecFg * (long) margeViseeBp, 10000);
    }
}
