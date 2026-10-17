package com.gregory.truelle.data;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit le texte reconnu sur une facture ou un ticket et en tire : montant TTC, TVA, date, fournisseur.
 * Le résultat est une proposition : l'écran demande toujours de vérifier avant de valider.
 * Aucune dépendance Android : testé sur ordinateur.
 */
public final class ReceiptParser {

    /** Une ligne de texte reconnue, avec sa position sur la photo (en pixels). */
    public static final class Ligne {
        public final String texte;
        public final int haut;
        public final int bas;
        public final int gauche;

        public Ligne(String texte, int haut, int bas, int gauche) {
            this.texte = texte == null ? "" : texte;
            this.haut = haut;
            this.bas = bas;
            this.gauche = gauche;
        }

        int centre() {
            return (haut + bas) / 2;
        }

        int hauteur() {
            return Math.max(1, bas - haut);
        }
    }

    public static final class Resultat {
        public long montantCentimes = -1;
        public long tvaCentimes = -1;
        public String date;        // jj/mm/aaaa
        public String fournisseur;

        public boolean montantTrouve() {
            return montantCentimes > 0;
        }
    }

    /** Fournisseurs courants du bâtiment, reconnus n'importe où dans le texte. */
    private static final String[][] FOURNISSEURS = {
            {"POINT P", "Point P"}, {"LEROY MERLIN", "Leroy Merlin"}, {"CASTORAMA", "Castorama"},
            {"BRICO DEPOT", "Brico Dépôt"}, {"BRICOMARCHE", "Bricomarché"}, {"MR BRICOLAGE", "Mr Bricolage"},
            {"MONSIEUR BRICOLAGE", "Mr Bricolage"}, {"WELDOM", "Weldom"}, {"GEDIMAT", "Gedimat"},
            {"BIGMAT", "BigMat"}, {"TOUT FAIRE", "Tout Faire"}, {"CEDEO", "Cedeo"}, {"WURTH", "Würth"},
            {"REXEL", "Rexel"}, {"SONEPAR", "Sonepar"}, {"YESSS", "Yesss Électrique"}, {"PROLIANS", "Prolians"},
            {"PLATEFORME DU BATIMENT", "La Plateforme du Bâtiment"}, {"BRICO CASH", "Brico Cash"},
            {"KILOUTOU", "Kiloutou"}, {"LOXAM", "Loxam"}, {"SAMSE", "Samse"}, {"DISPANO", "Dispano"},
            {"RICHARDSON", "Richardson"}, {"CHAUSSON", "Chausson Matériaux"}, {"PAREDES", "Paredes"},
            {"SIDER", "Sider"}, {"DOUILLET", "Douillet"}, {"IKEA", "Ikea"},
            {"TOTALENERGIES", "TotalEnergies"}, {"TOTAL ENERGIES", "TotalEnergies"}, {"ESSO", "Esso"},
            {"INTERMARCHE", "Intermarché"}, {"CARREFOUR", "Carrefour"}, {"LECLERC", "E.Leclerc"},
            {"AUCHAN", "Auchan"}, {"SUPER U", "Super U"}, {"HYPER U", "Hyper U"},
    };

    /** Mots-clés du total, du plus fiable au moins fiable. */
    private static final String[] CLES_TOTAL = {
            "NET A PAYER", "TOTAL TTC", "MONTANT TTC", "TOTAL A PAYER", "A PAYER", "TOTAL EUR", "TTC", "TOTAL", "CB", "CARTE",
    };

    private static final String[] EXCLUS_TOTAL = {"SOUS-TOTAL", "SOUS TOTAL", "TOTAL HT", "TOTAL H.T", "TOTAL TVA", "HT ", "REMISE", "ECONOMIE", "RENDU", "POINTS"};

    private static final String[] PAS_UN_NOM = {
            "FACTURE", "TICKET", "DATE", "TEL", "TÉL", "SIRET", "SIREN", "CLIENT", "N°", "NO ", "TVA", "CAISSE",
            "BIENVENUE", "MERCI", "WWW", "HTTP", "@", "ADRESSE", "RCS", "APE", "COMMANDE", "LIVRAISON", "PAGE",
    };

    // 1 234,56 / 1234.56 / 1.234,56 / 12,50 € — deux décimales obligatoires.
    private static final Pattern MONTANT = Pattern.compile("(?<![\\d,.])(\\d{1,3}(?:[ .\\u202f\\u00a0]\\d{3})+|\\d+)[,.](\\d{2})(?![\\d%])(?!\\s*%)");
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?!\\d)");

    private ReceiptParser() {}

    public static Resultat analyser(List<Ligne> lignes) {
        Resultat r = new Resultat();
        if (lignes == null || lignes.isEmpty()) return r;
        r.date = trouverDate(lignes);
        r.montantCentimes = trouverTotal(lignes);
        r.tvaCentimes = trouverTva(lignes, r.montantCentimes);
        r.fournisseur = trouverFournisseur(lignes);
        return r;
    }

    // ------------------------------------------------------------ total

    private static long trouverTotal(List<Ligne> lignes) {
        for (String cle : CLES_TOTAL) {
            long meilleur = -1;
            for (Ligne l : lignes) {
                String n = normaliser(l.texte);
                if (!contientMot(n, cle) || estExclu(n)) continue;
                long m = maxMontant(montantsDeLaLigneEtVoisines(l, lignes));
                if (m > meilleur) meilleur = m;
            }
            if (meilleur > 0) return meilleur;
        }
        // Rien trouvé avec un mot-clé : on prend le plus grand montant hors dates.
        long max = -1;
        for (Ligne l : lignes) max = Math.max(max, maxMontant(montants(l.texte)));
        return max;
    }

    private static boolean estExclu(String n) {
        for (String x : EXCLUS_TOTAL) if (n.contains(x)) return true;
        return false;
    }

    /** Montants de la ligne, plus ceux des lignes alignées à sa droite (le prix est souvent un bloc séparé). */
    private static List<Long> montantsDeLaLigneEtVoisines(Ligne l, List<Ligne> toutes) {
        List<Long> res = new ArrayList<>(montants(l.texte));
        for (Ligne o : toutes) {
            if (o == l || o.gauche < l.gauche) continue;
            if (Math.abs(o.centre() - l.centre()) <= Math.max(l.hauteur(), o.hauteur()) * 0.6) {
                res.addAll(montants(o.texte));
            }
        }
        return res;
    }

    // ------------------------------------------------------------ TVA

    private static long trouverTva(List<Ligne> lignes, long total) {
        long meilleur = -1;
        for (Ligne l : lignes) {
            String n = normaliser(l.texte);
            if (!n.contains("TVA") && !n.contains("T.V.A")) continue;
            for (long m : montantsDeLaLigneEtVoisines(l, lignes)) {
                // La TVA est forcément plus petite que le total, et en général moins d'un tiers.
                if (m <= 0 || (total > 0 && m * 3 > total)) continue;
                if (m > meilleur) meilleur = m;
            }
        }
        return meilleur;
    }

    // ------------------------------------------------------------ date

    static String trouverDate(List<Ligne> lignes) {
        Calendar demain = Calendar.getInstance();
        demain.add(Calendar.DAY_OF_YEAR, 1);
        for (Ligne l : lignes) {
            Matcher m = DATE.matcher(l.texte);
            while (m.find()) {
                int j = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int a = Integer.parseInt(m.group(3));
                if (a < 100) a += 2000;
                if (j < 1 || j > 31 || mo < 1 || mo > 12 || a < 2000) continue;
                Calendar c = Calendar.getInstance();
                c.set(a, mo - 1, j, 0, 0, 0);
                if (c.after(demain)) continue; // une facture n'est pas datée dans le futur
                return String.format(Locale.FRANCE, "%02d/%02d/%04d", j, mo, a);
            }
        }
        return null;
    }

    // ------------------------------------------------------------ fournisseur

    static String trouverFournisseur(List<Ligne> lignes) {
        StringBuilder tout = new StringBuilder();
        for (Ligne l : lignes) tout.append(' ').append(normaliser(l.texte)).append(' ');
        String t = tout.toString().replaceAll("[^A-Z0-9]+", " "); // « POINT.P », « BRICO-DEPOT »…
        for (String[] f : FOURNISSEURS) {
            if (contientMot(t, f[0])) return f[1];
        }
        // Sinon : la première ligne en haut du document qui ressemble à un nom.
        List<Ligne> parHauteur = new ArrayList<>(lignes);
        parHauteur.sort((x, y) -> Integer.compare(x.haut, y.haut));
        int vues = 0;
        for (Ligne l : parHauteur) {
            if (vues++ >= 5) break;
            String brut = l.texte.trim();
            String n = normaliser(brut);
            if (lettres(brut) < 3 || !montants(brut).isEmpty() || DATE.matcher(brut).find()) continue;
            boolean exclu = false;
            for (String x : PAS_UN_NOM) if (n.contains(x)) { exclu = true; break; }
            if (exclu) continue;
            return casse(brut.length() > 40 ? brut.substring(0, 40) : brut);
        }
        return null;
    }

    // ------------------------------------------------------------ outils

    /** Tous les montants d'un texte, en centimes, en ignorant les dates et les pourcentages. */
    static List<Long> montants(String texte) {
        List<Long> res = new ArrayList<>();
        String sansDates = DATE.matcher(texte).replaceAll(" ");
        Matcher m = MONTANT.matcher(sansDates);
        while (m.find()) {
            String euros = m.group(1).replaceAll("[ .\\u202f\\u00a0]", "");
            try {
                long v = Long.parseLong(euros) * 100 + Long.parseLong(m.group(2));
                if (v > 0 && v < 100_000_000L) res.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return res;
    }

    private static long maxMontant(List<Long> l) {
        long max = -1;
        for (long v : l) max = Math.max(max, v);
        return max;
    }

    /** Majuscules sans accents, pour comparer sans se soucier de la casse ni des accents. */
    static String normaliser(String s) {
        String sansAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toUpperCase(Locale.FRANCE);
    }

    private static boolean contientMot(String texteNormalise, String mot) {
        int i = texteNormalise.indexOf(mot);
        while (i >= 0) {
            boolean debut = i == 0 || !Character.isLetterOrDigit(texteNormalise.charAt(i - 1));
            int fin = i + mot.length();
            boolean finOk = fin >= texteNormalise.length() || !Character.isLetterOrDigit(texteNormalise.charAt(fin));
            if (debut && finOk) return true;
            i = texteNormalise.indexOf(mot, i + 1);
        }
        return false;
    }

    private static int lettres(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) n++;
        return n;
    }

    /** « SARL DUPONT MATERIAUX » → « Sarl Dupont Materiaux ». */
    private static String casse(String s) {
        StringBuilder sb = new StringBuilder();
        boolean debutMot = true;
        for (char c : s.toCharArray()) {
            sb.append(debutMot ? Character.toUpperCase(c) : Character.toLowerCase(c));
            debutMot = !Character.isLetterOrDigit(c);
        }
        return sb.toString().trim();
    }
}
