package com.gregory.truelle.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Dates au format de l'appli, « jj/mm/aaaa », comme partout dans le journal. */
public final class Dates {

    private Dates() {}

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final String[] JOURS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};
    private static final String[] JOURS_COURTS = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};
    private static final String[] MOIS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août",
            "septembre", "octobre", "novembre", "décembre"};

    /** Date du jour, remplaçable dans les tests. */
    static LocalDate aujourdhuiFixe = null;

    public static LocalDate aujourdhuiDate() {
        return aujourdhuiFixe != null ? aujourdhuiFixe : LocalDate.now();
    }

    public static String aujourdhui() {
        return format(aujourdhuiDate());
    }

    public static String format(LocalDate d) {
        return d == null ? "" : d.format(FORMAT);
    }

    /** « 21/10/2026 » → date ; null si vide ou illisible. */
    public static LocalDate parse(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(s.trim(), FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    /** Clé de tri : aaaammjj ; les dates vides ou illisibles à la fin. */
    public static long cle(String s) {
        LocalDate d = parse(s);
        return d == null ? Long.MAX_VALUE : d.getYear() * 10000L + d.getMonthValue() * 100L + d.getDayOfMonth();
    }

    public static boolean memeJour(String s, LocalDate jour) {
        LocalDate d = parse(s);
        return d != null && d.equals(jour);
    }

    /** Vrai si la date est passée (avant aujourd'hui). */
    public static boolean passee(String s) {
        LocalDate d = parse(s);
        return d != null && d.isBefore(aujourdhuiDate());
    }

    /** Nombre de jours écoulés depuis cette date (négatif si elle est à venir) ; 0 si vide. */
    public static long joursDepuis(String s) {
        LocalDate d = parse(s);
        return d == null ? 0 : ChronoUnit.DAYS.between(d, aujourdhuiDate());
    }

    public static LocalDate lundi(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    /** « Aujourd'hui », « Demain », « Hier », sinon « jeu. 22 oct. ». */
    public static String relatif(String s) {
        LocalDate d = parse(s);
        if (d == null) return "";
        long ecart = ChronoUnit.DAYS.between(aujourdhuiDate(), d);
        if (ecart == 0) return "Aujourd'hui";
        if (ecart == 1) return "Demain";
        if (ecart == -1) return "Hier";
        return JOURS_COURTS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + " " + MOIS[d.getMonthValue() - 1].substring(0, Math.min(4, MOIS[d.getMonthValue() - 1].length()));
    }

    /** « mardi 20 octobre ». */
    public static String long_(LocalDate d) {
        return JOURS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + " " + MOIS[d.getMonthValue() - 1];
    }

    public static String jourCourt(LocalDate d) {
        return JOURS_COURTS[d.getDayOfWeek().getValue() - 1];
    }

    public static String mois(int mois) {
        return MOIS[mois - 1];
    }

    /** Vrai si la date tombe dans le mois donné (année + mois). */
    public static boolean dansMois(String s, int annee, int mois) {
        LocalDate d = parse(s);
        return d != null && d.getYear() == annee && d.getMonthValue() == mois;
    }
}
