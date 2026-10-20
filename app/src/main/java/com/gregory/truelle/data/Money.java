package com.gregory.truelle.data;

/** Les montants sont toujours stockés en centimes (nombre entier) pour éviter les erreurs d'arrondi. */
public final class Money {

    private Money() {}

    /**
     * Convertit une saisie (« 12,50 », « 12.5 », « 1 234,00 € ») en centimes.
     * Retourne -1 si la saisie n'est pas un montant valable.
     */
    public static long parseCentimes(String input) {
        if (input == null) return -1;
        String s = input.replace("€", "").replace(" ", "").replace(" ", "").replace(" ", "").trim();
        if (s.isEmpty()) return -1;
        s = s.replace(',', '.');
        if (!s.matches("\\d+(\\.\\d{0,2})?")) return -1;
        String[] parts = s.split("\\.", -1);
        long euros = Long.parseLong(parts[0]);
        long cents = 0;
        if (parts.length == 2 && !parts[1].isEmpty()) {
            String c = parts[1].length() == 1 ? parts[1] + "0" : parts[1];
            cents = Long.parseLong(c);
        }
        return euros * 100 + cents;
    }

    /** 123450 → « 1 234,50 € » (espace fine insécable entre les milliers). */
    public static String format(long centimes) {
        boolean negative = centimes < 0;
        long abs = Math.abs(centimes);
        String euros = Long.toString(abs / 100);
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = euros.length() - 1; i >= 0; i--) {
            grouped.insert(0, euros.charAt(i));
            if (++count % 3 == 0 && i > 0) grouped.insert(0, ' ');
        }
        long cents = abs % 100;
        return (negative ? "-" : "") + grouped + "," + (cents < 10 ? "0" : "") + cents + " €";
    }

    /** Arrondi à l'euro, pour les tableaux serrés : 123450 → « 1 235 € ». */
    public static String formatCourt(long centimes) {
        long e = Math.abs(centimes) / 100 + (Math.abs(centimes) % 100 >= 50 ? 1 : 0);
        String f = format(e * 100);
        return (centimes < 0 && e > 0 ? "-" : "") + f.substring(0, f.length() - 5) + " €";
    }

    /** Pour réafficher un montant dans un champ de saisie : 123450 → « 1234,50 ». */
    public static String toInput(long centimes) {
        long cents = centimes % 100;
        return (centimes / 100) + "," + (cents < 10 ? "0" : "") + cents;
    }
}
