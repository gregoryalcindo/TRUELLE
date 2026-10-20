package com.gregory.truelle.data;

/** Heures en centièmes : 750 = 7 h 30. */
public final class Heures {

    private Heures() {}

    /** « 7,5 », « 7h30 », « 7:30 », « 8 » → centièmes ; -1 si invalide. */
    public static long parse(String s) {
        if (s == null) return -1;
        String t = s.toLowerCase().replace(" ", "").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,3})[h:](\\d{1,2})?$").matcher(t);
        if (m.find()) {
            long h = Long.parseLong(m.group(1));
            long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
            if (min >= 60) return -1;
            return h * 100 + Tva.arrondi(min * 100, 60);
        }
        t = t.replace(',', '.');
        if (!t.matches("\\d{1,3}(\\.\\d{1,2})?")) return -1;
        String[] p = t.split("\\.");
        long v = Long.parseLong(p[0]) * 100;
        if (p.length == 2) v += Long.parseLong(p[1].length() == 1 ? p[1] + "0" : p[1]);
        return v;
    }

    /** 750 → « 7 h 30 » ; 800 → « 8 h ». */
    public static String format(long centiemes) {
        long h = centiemes / 100;
        long min = Tva.arrondi((centiemes % 100) * 60, 100);
        if (min == 60) {
            h++;
            min = 0;
        }
        return min == 0 ? h + " h" : h + " h " + (min < 10 ? "0" : "") + min;
    }

    /** 750 → « 7,5 » (pour un champ de saisie). */
    public static String toInput(long centiemes) {
        long r = centiemes % 100;
        if (r == 0) return Long.toString(centiemes / 100);
        return (centiemes / 100) + "," + (r % 10 == 0 ? Long.toString(r / 10) : (r < 10 ? "0" + r : Long.toString(r)));
    }
}
