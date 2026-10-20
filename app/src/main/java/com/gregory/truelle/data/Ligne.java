package com.gregory.truelle.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Une ligne de devis : désignation, quantité × prix unitaire HT, taux de TVA,
 * et en option le coût prévu (déboursé) et son poste, pour le suivi prévu / réel.
 */
public final class Ligne {

    public String designation = "";
    /** Quantité en millièmes (1,5 m² = 1500). */
    public long quantiteMilli = 1000;
    public String unite = "u";
    public long puHtCentimes = 0;
    public int tvaBp = Tva.NORMAL;
    /** Coût prévu total de la ligne (HT), 0 si non chiffré. */
    public long coutPrevuCentimes = 0;
    public String poste = Postes.MATERIAUX;

    public static final String[] UNITES = {"u", "m²", "ml", "m³", "h", "j", "kg", "forfait"};

    public long totalHt() {
        return Tva.arrondi(quantiteMilli * puHtCentimes, 1000);
    }

    public long totalTva() {
        return Tva.tva(totalHt(), tvaBp);
    }

    public Ligne copie() {
        Ligne l = new Ligne();
        l.designation = designation;
        l.quantiteMilli = quantiteMilli;
        l.unite = unite;
        l.puHtCentimes = puHtCentimes;
        l.tvaBp = tvaBp;
        l.coutPrevuCentimes = coutPrevuCentimes;
        l.poste = poste;
        return l;
    }

    // ------------------------------------------------------------ quantités

    /** « 1,5 » → 1500 ; -1 si invalide. */
    public static long parseQuantite(String s) {
        if (s == null) return -1;
        String t = s.replace(',', '.').replace(" ", "").trim();
        if (!t.matches("\\d+(\\.\\d{1,3})?")) return -1;
        String[] p = t.split("\\.");
        long v = Long.parseLong(p[0]) * 1000;
        if (p.length == 2) {
            String d = (p[1] + "00").substring(0, 3);
            v += Long.parseLong(d);
        }
        return v;
    }

    /** 1500 → « 1,5 » ; 2000 → « 2 ». */
    public static String formatQuantite(long milli) {
        String s = Long.toString(milli / 1000);
        long r = milli % 1000;
        if (r == 0) return s;
        String d = String.format("%03d", r).replaceAll("0+$", "");
        return s + "," + d;
    }

    // ------------------------------------------------------------ stockage dans le journal

    private static final char SEP = '\u001e';

    /** Plusieurs lignes dans un seul champ texte du journal. */
    public static String encoder(List<Ligne> lignes) {
        StringBuilder sb = new StringBuilder();
        for (Ligne l : lignes) {
            if (sb.length() > 0) sb.append(SEP);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("d", l.designation);
            m.put("q", Long.toString(l.quantiteMilli));
            m.put("u", l.unite);
            m.put("pu", Long.toString(l.puHtCentimes));
            m.put("tva", Integer.toString(l.tvaBp));
            m.put("cp", Long.toString(l.coutPrevuCentimes));
            m.put("p", l.poste);
            sb.append(JsonLite.write(m));
        }
        return sb.toString();
    }

    public static List<Ligne> decoder(String s) {
        List<Ligne> res = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) return res;
        for (String part : s.split(String.valueOf(SEP))) {
            try {
                Map<String, String> m = JsonLite.read(part);
                Ligne l = new Ligne();
                l.designation = val(m, "d", "");
                l.quantiteMilli = num(m, "q", 1000);
                l.unite = val(m, "u", "u");
                l.puHtCentimes = num(m, "pu", 0);
                l.tvaBp = (int) num(m, "tva", Tva.NORMAL);
                l.coutPrevuCentimes = num(m, "cp", 0);
                l.poste = Postes.normaliser(val(m, "p", Postes.MATERIAUX));
                res.add(l);
            } catch (Exception ignored) {
                // ligne abîmée : ignorée
            }
        }
        return res;
    }

    private static String val(Map<String, String> m, String k, String d) {
        String v = m.get(k);
        return v == null ? d : v;
    }

    private static long num(Map<String, String> m, String k, long d) {
        try {
            return Long.parseLong(m.get(k));
        } catch (Exception e) {
            return d;
        }
    }
}
