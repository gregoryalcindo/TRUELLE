package com.gregory.truelle.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tous les calculs financiers d'un chantier, à partir du journal et des réglages.
 * Voir docs/suivi-financier.md.
 *
 * Quatre niveaux jamais mélangés : chiffré (devis), validé (signé / accepté),
 * réalisé (dépenses + heures), facturé / encaissé.
 * Les coûts se comptent en TTC (TVA non récupérée, réglage par défaut) ou en HT si la TVA est récupérée.
 * Le chiffre d'affaires de l'entreprise est toujours hors TVA facturée (la TVA facturée est due à l'État).
 */
public final class Finance {

    public final Chantier c;
    public final Reglages r;

    public Finance(Chantier c, Reglages r) {
        this.c = c;
        this.r = r == null ? new Reglages() : r;
    }

    public int tvaDefaut() {
        return c.entreProches() ? Tva.AUCUNE : r.tvaPour(c);
    }

    /** Entre proches : fonctionne comme un chantier pro, mais sans aucune TVA (chantier non déclaré). */
    public boolean proches() {
        return c.entreProches();
    }

    public boolean recupere() {
        return r.recupereTva && !proches();
    }

    // ================================================================ totaux d'un ensemble de lignes

    /** Totaux d'un devis : HT avant / après remise, TVA par taux, TTC, coût prévu. */
    public static final class Totaux {
        public long htBrut;
        public long remise;
        public long ht;
        /** Base HT (après remise) et TVA, par taux. */
        public final Map<Integer, Long> baseParTaux = new TreeMap<>();
        public final Map<Integer, Long> tvaParTaux = new TreeMap<>();
        public long tva;
        public long ttc;
        public long coutPrevu;
        public final Map<String, Long> coutPrevuParPoste = new LinkedHashMap<>();
    }

    public static Totaux totaux(List<Ligne> lignes, int remiseBp, boolean sansTva) {
        Totaux t = new Totaux();
        Map<Integer, Long> brutParTaux = new TreeMap<>();
        for (Ligne l : lignes) {
            long ht = l.totalHt();
            t.htBrut += ht;
            int taux = sansTva ? 0 : l.tvaBp;
            brutParTaux.merge(taux, ht, Long::sum);
            t.coutPrevu += l.coutPrevuCentimes;
            if (l.coutPrevuCentimes != 0) t.coutPrevuParPoste.merge(l.poste, l.coutPrevuCentimes, Long::sum);
        }
        for (Map.Entry<Integer, Long> e : brutParTaux.entrySet()) {
            long rem = Tva.arrondi(e.getValue() * (long) remiseBp, 10000);
            long base = e.getValue() - rem;
            long tva = Tva.tva(base, e.getKey());
            t.remise += rem;
            t.baseParTaux.put(e.getKey(), base);
            t.tvaParTaux.put(e.getKey(), tva);
            t.tva += tva;
        }
        t.ht = t.htBrut - t.remise;
        t.ttc = t.ht + t.tva;
        return t;
    }

    // ================================================================ devis

    public static boolean estNotreDevis(Chantier.Element d) {
        return !Chantier.DEVIS_SOUS_TRAITANT.equals(d.get("sorte"));
    }

    public static boolean signe(Chantier.Element d) {
        return Chantier.DEVIS_SIGNE.equals(d.get("statut"));
    }

    /** Lignes d'un devis ; un ancien devis à montant unique (TTC) devient une ligne. */
    public List<Ligne> lignes(Chantier.Element devis) {
        List<Ligne> l = Ligne.decoder(devis.get("lignes"));
        if (!l.isEmpty() || devis.get("montant_centimes").isEmpty()) return l;
        Ligne seule = new Ligne();
        seule.designation = devis.get("libelle").isEmpty() ? "Devis" : devis.get("libelle");
        seule.quantiteMilli = 1000;
        seule.unite = "forfait";
        int bp = autoliquidation(devis) ? 0 : tvaDefaut();
        seule.tvaBp = bp;
        seule.puHtCentimes = Tva.ht(devis.centimes("montant_centimes"), bp);
        seule.poste = estNotreDevis(devis) ? Postes.MATERIAUX : Postes.SOUS_TRAITANCE;
        List<Ligne> res = new ArrayList<>();
        res.add(seule);
        return res;
    }

    public static boolean autoliquidation(Chantier.Element devis) {
        return "oui".equals(devis.get("autoliquidation"));
    }

    public Totaux totaux(Chantier.Element devis) {
        boolean sansTva = autoliquidation(devis) || (r.franchise() || proches()) && estNotreDevis(devis);
        return totaux(lignes(devis), (int) devis.centimes("remise_bp"), sansTva);
    }

    // ================================================================ travaux en plus (avenants)

    public long htTravaux(Chantier.Element t) {
        if (!t.get("ht_centimes").isEmpty()) return t.centimes("ht_centimes");
        return Tva.ht(t.centimes("montant_centimes"), tvaTravaux(t));
    }

    public int tvaTravaux(Chantier.Element t) {
        if (proches()) return Tva.AUCUNE;
        if (!t.get("tva_bp").isEmpty()) return (int) t.centimes("tva_bp");
        return tvaDefaut();
    }

    public long ttcTravaux(Chantier.Element t) {
        return Tva.ttc(htTravaux(t), tvaTravaux(t));
    }

    public static boolean accepte(Chantier.Element t) {
        String s = t.get("statut");
        return Chantier.TRAVAUX_ACCEPTE.equals(s) || Chantier.TRAVAUX_FAIT.equals(s);
    }

    // ================================================================ dépenses

    /** Taux de TVA d'une dépense : choisi, sinon déduit de la TVA lue, sinon taux normal. */
    public static int tvaDepense(Chantier.Depense d) {
        if (d.tvaBp >= 0) return d.tvaBp;
        if (d.tvaCentimes >= 0) {
            int t = Tva.deduire(d.montantCentimes, d.tvaCentimes);
            if (t >= 0) return t;
        }
        return Tva.NORMAL;
    }

    public static long htDepense(Chantier.Depense d) {
        if (d.tvaBp < 0 && d.tvaCentimes >= 0 && d.tvaCentimes < d.montantCentimes) return d.montantCentimes - d.tvaCentimes;
        return Tva.ht(d.montantCentimes, tvaDepense(d));
    }

    /** Coût pour le chantier : TTC (TVA non récupérée), ou HT si la TVA est récupérée. */
    public long coutDepense(Chantier.Depense d) {
        return recupere() ? htDepense(d) : d.montantCentimes;
    }

    public static String posteDepense(Chantier.Depense d) {
        return Postes.deCategorie(d.categorie);
    }

    /** Montant refacturé au client, hors TVA facturée : coût d'achat + majoration. */
    public long refactureHt(Chantier.Depense d) {
        long base = coutDepense(d);
        return base + Tva.arrondi(base * (long) c.majorationPct, 100);
    }

    public int aVerifier() {
        int n = 0;
        for (Chantier.Depense d : c.depenses()) if (!d.valide) n++;
        return n;
    }

    // ================================================================ main d'œuvre

    /** Coût horaire chargé (réglages de l'appli), sauf taux saisi sur la ligne d'heures. */
    public long tauxHeure(Chantier.Heure h) {
        if (h.tauxCentimes >= 0) return h.tauxCentimes;
        return r.tauxHoraireCentimes;
    }

    /** Coût d'une ligne d'heures. La main d'œuvre vendue au client passe par les lignes des devis. */
    public long coutHeure(Chantier.Heure h) {
        return Tva.arrondi(h.heuresCentiemes * tauxHeure(h), 100);
    }

    public long totalHeuresCentiemes() {
        long t = 0;
        for (Chantier.Heure h : c.heures()) t += h.heuresCentiemes;
        return t;
    }

    // ================================================================ VALIDÉ : ce que le client paiera

    public long devisSignesHt() {
        long t = 0;
        for (Chantier.Element d : c.elements(Event.DEVIS)) if (estNotreDevis(d) && signe(d)) t += totaux(d).ht;
        return t;
    }

    public long travauxAcceptesHt() {
        long t = 0;
        for (Chantier.Element el : c.elements(Event.TRAVAUX)) if (accepte(el)) t += htTravaux(el);
        return t;
    }

    public long materielRefactureHt() {
        long t = 0;
        for (Chantier.Depense d : c.depenses()) if (c.estARefacturer(d)) t += refactureHt(d);
        return t;
    }

    /** Chiffre d'affaires validé hors TVA (entre proches : sans TVA, donc le montant payé). */
    public long venduHt() {
        return devisSignesHt() + travauxAcceptesHt() + materielRefactureHt();
    }

    /** CHIFFRÉ : nos devis en cours (brouillon, envoyés, signés), pour information. */
    public long chiffreHt() {
        long t = 0;
        for (Chantier.Element d : c.elements(Event.DEVIS)) {
            String s = d.get("statut");
            if (estNotreDevis(d) && !Chantier.DEVIS_REFUSE.equals(s) && !Chantier.DEVIS_REMPLACE.equals(s)) t += totaux(d).ht;
        }
        return t;
    }

    // ================================================================ PRÉVU / RÉEL par poste

    /** Coûts prévus : coûts des lignes des devis signés et des travaux en plus acceptés. */
    public Map<String, Long> prevuParPoste() {
        Map<String, Long> m = postesVides();
        for (Chantier.Element d : c.elements(Event.DEVIS)) {
            if (!estNotreDevis(d) || !signe(d)) continue;
            for (Map.Entry<String, Long> e : totaux(d).coutPrevuParPoste.entrySet()) m.merge(e.getKey(), e.getValue(), Long::sum);
        }
        for (Chantier.Element t : c.elements(Event.TRAVAUX)) {
            if (accepte(t) && t.centimes("cout_prevu_centimes") > 0) {
                m.merge(Postes.normaliser(t.get("poste")), t.centimes("cout_prevu_centimes"), Long::sum);
            }
        }
        // Ancien « budget matériel » : utilisé s'il n'y a aucun coût prévu de matériaux dans les devis.
        if (m.get(Postes.MATERIAUX) == 0 && c.budgetMaterielCentimes > 0) m.put(Postes.MATERIAUX, c.budgetMaterielCentimes);
        return m;
    }

    /** Devis de sous-traitants acceptés (montant de coût : TTC, ou HT si TVA récupérée). */
    public long sousTraitanceAccepteeCout() {
        long t = 0;
        for (Chantier.Element d : c.elements(Event.DEVIS)) {
            if (estNotreDevis(d) || !signe(d)) continue;
            Totaux tx = totaux(d);
            t += recupere() ? tx.ht : tx.ttc;
        }
        return t;
    }

    /** DÉPENSÉ par poste : dépenses (vérifiées ou non) + heures. */
    public Map<String, Long> depenseParPoste() {
        Map<String, Long> m = postesVides();
        for (Chantier.Depense d : c.depenses()) m.merge(posteDepense(d), coutDepense(d), Long::sum);
        for (Chantier.Heure h : c.heures()) m.merge(Postes.MAIN_OEUVRE, coutHeure(h), Long::sum);
        return m;
    }

    /** ENGAGÉ (pas encore payé) par poste : devis de sous-traitants acceptés non encore facturés. */
    public Map<String, Long> engageParPoste() {
        Map<String, Long> m = postesVides();
        long dejaFacture = depenseParPoste().get(Postes.SOUS_TRAITANCE);
        m.put(Postes.SOUS_TRAITANCE, Math.max(0, sousTraitanceAccepteeCout() - dejaFacture));
        return m;
    }

    /** RÉEL = dépensé + engagé : ce que le chantier coûte déjà, payé ou promis. */
    public Map<String, Long> reelParPoste() {
        Map<String, Long> m = depenseParPoste();
        for (Map.Entry<String, Long> e : engageParPoste().entrySet()) m.merge(e.getKey(), e.getValue(), Long::sum);
        return m;
    }

    /** DISPONIBLE = budget − dépensé − engagé (négatif = dépassement). */
    public long disponible(String poste) {
        return prevuParPoste().get(poste) - reelParPoste().get(poste);
    }

    /** Niveau d'alerte d'un poste : 0 normal, 1 attention (≥ 80 % du budget), 2 dépassé (> 100 %). */
    public int alerte(String poste) {
        long budget = prevuParPoste().get(poste);
        long conso = reelParPoste().get(poste);
        if (budget <= 0) return 0;
        if (conso > budget) return 2;
        return conso * 10 >= budget * 8 ? 1 : 0;
    }

    /** % du budget total consommé (dépensé + engagé), en points de base ; -1 sans budget. */
    public long consommeBp() {
        long budget = somme(prevuParPoste());
        return budget <= 0 ? -1 : Tva.arrondi(somme(reelParPoste()) * 10000L, budget);
    }

    /**
     * Prévision du coût final (déboursé sec) : pour chaque poste, le plus grand du budget
     * et de ce qui est déjà dépensé ou engagé. Simple et prudent.
     */
    public long previsionDeboursSec() {
        Map<String, Long> b = prevuParPoste();
        Map<String, Long> re = reelParPoste();
        long t = 0;
        for (String[] p : Postes.TOUS) t += Math.max(b.get(p[0]), re.get(p[0]));
        return t;
    }

    public Synthese prevision() {
        return synthese(previsionDeboursSec());
    }

    private static Map<String, Long> postesVides() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String[] p : Postes.TOUS) m.put(p[0], 0L);
        return m;
    }

    public static long somme(Map<String, Long> m) {
        long t = 0;
        for (long v : m.values()) t += v;
        return t;
    }

    public long fraisGeneraux(long deboursSec) {
        return Tva.arrondi(deboursSec * (long) r.fraisGenerauxBp, 10000);
    }

    /** Synthèse prévu ou réel : déboursé sec → frais généraux → coût de revient → bénéfice. */
    public static final class Synthese {
        public long deboursSec;
        public long fraisGeneraux;
        public long coutRevient;
        public long venteHt;
        public long benefice;

        /** Bénéfice en points de base du chiffre d'affaires ; 0 si pas de CA. */
        public long beneficeBp() {
            return venteHt == 0 ? 0 : Tva.arrondi(benefice * 10000L, venteHt);
        }
    }

    public Synthese prevu() {
        return synthese(somme(prevuParPoste()));
    }

    public Synthese reel() {
        return synthese(somme(reelParPoste()));
    }

    private Synthese synthese(long ds) {
        Synthese s = new Synthese();
        s.deboursSec = ds;
        s.fraisGeneraux = fraisGeneraux(ds);
        s.coutRevient = ds + s.fraisGeneraux;
        s.venteHt = venduHt();
        s.benefice = s.venteHt - s.coutRevient;
        return s;
    }

    // ================================================================ facturation

    public long htFacture(Chantier.Element f) {
        if (!f.get("ht_centimes").isEmpty()) return f.centimes("ht_centimes");
        return Tva.ht(f.centimes("montant_centimes"), tvaFacture(f));
    }

    public int tvaFacture(Chantier.Element f) {
        if (proches()) return Tva.AUCUNE;
        if (!f.get("tva_bp").isEmpty()) return (int) f.centimes("tva_bp");
        return tvaDefaut();
    }

    public long ttcFacture(Chantier.Element f) {
        return Tva.ttc(htFacture(f), tvaFacture(f));
    }

    /** Retenue de garantie sur cette facture (sur le TTC). */
    public long retenue(Chantier.Element f) {
        long bp = f.get("retenue_bp").isEmpty() ? 0 : f.centimes("retenue_bp");
        return Tva.arrondi(ttcFacture(f) * bp, 10000);
    }

    /** Net à payer par le client = TTC − retenue de garantie. */
    public long netAPayer(Chantier.Element f) {
        return ttcFacture(f) - retenue(f);
    }

    public static boolean facturee(Chantier.Element f) {
        String s = f.get("statut");
        return Chantier.FACT_FACTURE.equals(s) || Chantier.FACT_ENCAISSE.equals(s);
    }

    public long factureHt() {
        long t = 0;
        for (Chantier.Element f : c.elements(Event.FACTURATION)) if (facturee(f)) t += htFacture(f);
        return t;
    }

    public long factureTtc() {
        long t = 0;
        for (Chantier.Element f : c.elements(Event.FACTURATION)) if (facturee(f)) t += ttcFacture(f);
        return t;
    }

    public long encaisse() {
        long t = 0;
        for (Chantier.Element f : c.elements(Event.FACTURATION)) {
            if (Chantier.FACT_ENCAISSE.equals(f.get("statut"))) t += netAPayer(f);
        }
        return t;
    }

    /** Facturé mais pas encore payé (hors retenues). */
    public long enAttenteDePaiement() {
        long t = 0;
        for (Chantier.Element f : c.elements(Event.FACTURATION)) {
            if (Chantier.FACT_FACTURE.equals(f.get("statut"))) t += netAPayer(f);
        }
        return t;
    }

    public long retenues() {
        long t = 0;
        for (Chantier.Element f : c.elements(Event.FACTURATION)) if (facturee(f)) t += retenue(f);
        return t;
    }

    public long resteAFacturerHt() {
        return Math.max(0, venduHt() - factureHt());
    }

    // ================================================================ TVA (information)

    public long tvaCollectee() {
        long t = 0;
        for (Chantier.Element f : c.elements(Event.FACTURATION)) if (facturee(f)) t += ttcFacture(f) - htFacture(f);
        return t;
    }

    public long tvaDeductible() {
        if (!recupere()) return 0;
        long t = 0;
        for (Chantier.Depense d : c.depenses()) t += d.montantCentimes - htDepense(d);
        return t;
    }

    // ================================================================ aides

    /** Base pour une facture de x % : % du CA HT validé. */
    public long pourcentageDuVendu(int bp) {
        return Tva.arrondi(venduHt() * (long) bp, 10000);
    }

    /** Écart réel − prévu en points de base du prévu (0 si pas de prévu). */
    public static long ecartBp(long prevu, long reel) {
        return prevu == 0 ? 0 : Tva.arrondi((reel - prevu) * 10000L, prevu);
    }

    /** « 12,3 % » à partir de points de base. */
    public static String pourcent(long bp) {
        String s = Tva.libelle((int) Math.abs(bp));
        return (bp < 0 ? "-" : "") + s;
    }
}
