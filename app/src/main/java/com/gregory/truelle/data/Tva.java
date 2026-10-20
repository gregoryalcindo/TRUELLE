package com.gregory.truelle.data;

import java.util.Locale;

/**
 * Taux de TVA en points de base : 2000 = 20 %, 550 = 5,5 %, 0 = pas de TVA.
 * Arrondis au centime, à la moitié supérieure (règle usuelle de facturation).
 * Références : CGI art. 278 (20 %), 279-0 bis (10 %), 278-0 bis A (5,5 %),
 * 283-2 nonies (autoliquidation sous-traitance BTP), 293 B (franchise).
 */
public final class Tva {

    public static final int NORMAL = 2000;
    public static final int INTERMEDIAIRE = 1000;
    public static final int REDUIT = 550;
    public static final int PARTICULIER = 210;
    public static final int AUCUNE = 0;

    /** Taux proposés en boutons ; « autre » permet n'importe quelle valeur de 0 à 100 %. */
    public static final int[] COURANTS = {AUCUNE, REDUIT, INTERMEDIAIRE, NORMAL};

    private Tva() {}

    public static long tva(long htCentimes, int bp) {
        return arrondi(htCentimes * (long) bp, 10000);
    }

    public static long ttc(long htCentimes, int bp) {
        return htCentimes + tva(htCentimes, bp);
    }

    /** HT à partir du TTC (ticket de caisse). */
    public static long ht(long ttcCentimes, int bp) {
        return arrondi(ttcCentimes * 10000L, 10000L + bp);
    }

    /** « 20 % », « 5,5 % », « 0 % ». */
    public static String libelle(int bp) {
        if (bp % 100 == 0) return (bp / 100) + " %";
        String s = String.format(Locale.FRANCE, "%.2f", bp / 100.0);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s + " %";
    }

    /** « 5,5 » ou « 5.5 » ou « 20 » → points de base ; -1 si invalide (hors 0..100 %). */
    public static int parse(String saisie) {
        if (saisie == null) return -1;
        String s = saisie.replace("%", "").replace(',', '.').trim();
        if (!s.matches("\\d{1,3}(\\.\\d{1,2})?")) return -1;
        String[] p = s.split("\\.");
        int bp = Integer.parseInt(p[0]) * 100;
        if (p.length == 2) bp += Integer.parseInt(p[1].length() == 1 ? p[1] + "0" : p[1]);
        return bp > 10000 ? -1 : bp;
    }

    /**
     * Devine le taux à partir d'un TTC et d'une TVA lus sur une facture :
     * le taux courant le plus proche (à 0,3 point près), sinon le taux exact.
     */
    public static int deduire(long ttcCentimes, long tvaCentimes) {
        long ht = ttcCentimes - tvaCentimes;
        if (ht <= 0 || tvaCentimes < 0) return -1;
        int exact = (int) arrondi(tvaCentimes * 10000L, ht);
        for (int t : new int[]{NORMAL, INTERMEDIAIRE, REDUIT, PARTICULIER, AUCUNE}) {
            if (Math.abs(exact - t) <= 30) return t;
        }
        return Math.max(0, Math.min(10000, exact));
    }

    static long arrondi(long num, long den) {
        if (den == 0) return 0;
        boolean neg = (num < 0) ^ (den < 0);
        long a = Math.abs(num), b = Math.abs(den);
        long q = (a + b / 2) / b;
        return neg ? -q : q;
    }
}
