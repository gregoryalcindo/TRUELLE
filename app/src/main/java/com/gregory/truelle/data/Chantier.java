package com.gregory.truelle.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * État d'un chantier, recalculé en relisant son journal dans l'ordre.
 * Rien n'est stocké ici directement : tout vient des événements.
 */
public final class Chantier {

    public static final String A_DEMARRER = "a_demarrer";
    public static final String EN_COURS = "en_cours";
    public static final String TERMINE = "termine";

    public static final String MODE_REEL = "reel";
    public static final String MODE_FORFAIT = "forfait";

    public final String id;
    public String dossier = "";
    public String titre = "";
    public String client = "";
    public String adresse = "";
    public String mode = MODE_FORFAIT;
    public int majorationPct = 0;
    public String statut = A_DEMARRER;
    public long creeLe;
    public long modifieLe;

    private final Map<String, Depense> depenses = new LinkedHashMap<>();
    private final Map<String, Note> notes = new LinkedHashMap<>();

    public Chantier(String id) {
        this.id = id;
    }

    public static final class Depense {
        public final String id;
        public long montantCentimes;
        public String fournisseur;
        public String date;
        /** "auto" = suit le mode du chantier, "oui" / "non" = choix forcé sur cette dépense. */
        public String refacturer;
        public final String auteur;
        public final long creeLe;

        Depense(String id, String auteur, long creeLe) {
            this.id = id;
            this.auteur = auteur;
            this.creeLe = creeLe;
        }
    }

    public static final class Note {
        public final String id;
        public String texte;
        public final String auteur;
        public final long creeLe;

        Note(String id, String texte, String auteur, long creeLe) {
            this.id = id;
            this.texte = texte;
            this.auteur = auteur;
            this.creeLe = creeLe;
        }
    }

    /** Une ligne de la rubrique « Derniers ajouts » de la fiche chantier. */
    public static final class Ajout {
        public final String type; // Event.DEPENSE ou Event.NOTE
        public final String id;
        public final long at;

        Ajout(String type, String id, long at) {
            this.type = type;
            this.id = id;
            this.at = at;
        }
    }

    /** Relit les événements (dans n'importe quel ordre) et reconstruit l'état. */
    public static Chantier replay(String id, List<Event> events) {
        List<Event> sorted = new ArrayList<>(events);
        Collections.sort(sorted, ORDER);
        Chantier c = new Chantier(id);
        for (Event e : sorted) c.apply(e);
        return c;
    }

    /** Ordre stable : date, puis identifiant, pour que les deux téléphones obtiennent le même résultat. */
    public static final Comparator<Event> ORDER = (a, b) -> {
        int cmp = Long.compare(a.at, b.at);
        return cmp != 0 ? cmp : a.id.compareTo(b.id);
    };

    private void apply(Event e) {
        modifieLe = Math.max(modifieLe, e.at);
        switch (e.type) {
            case Event.CHANTIER_CREE:
                creeLe = e.at;
                applyInfos(e);
                break;
            case Event.CHANTIER_MODIFIE:
                applyInfos(e);
                break;
            case Event.STATUT:
                String v = e.get("valeur");
                if (A_DEMARRER.equals(v) || EN_COURS.equals(v) || TERMINE.equals(v)) statut = v;
                break;
            case Event.DEPENSE: {
                Depense d = new Depense(e.id, e.auteur, e.at);
                d.montantCentimes = parseLong(e.get("montant_centimes"), 0);
                d.fournisseur = e.get("fournisseur", "");
                d.date = e.get("date", "");
                d.refacturer = e.get("refacturer", "auto");
                depenses.put(e.id, d);
                break;
            }
            case Event.NOTE:
                notes.put(e.id, new Note(e.id, e.get("texte", ""), e.auteur, e.at));
                break;
            case Event.CORRECTION:
                applyCorrection(e);
                break;
            case Event.SUPPRESSION: {
                String cible = e.get("cible");
                if (cible != null) {
                    depenses.remove(cible);
                    notes.remove(cible);
                }
                break;
            }
            default:
                // Type inconnu (créé par une version plus récente de l'appli) : ignoré sans planter.
                break;
        }
    }

    private void applyInfos(Event e) {
        if (e.get("dossier") != null) dossier = e.get("dossier");
        if (e.get("titre") != null) titre = e.get("titre");
        if (e.get("client") != null) client = e.get("client");
        if (e.get("adresse") != null) adresse = e.get("adresse");
        if (e.get("mode") != null) mode = MODE_REEL.equals(e.get("mode")) ? MODE_REEL : MODE_FORFAIT;
        if (e.get("majoration_pct") != null) majorationPct = (int) parseLong(e.get("majoration_pct"), 0);
    }

    private void applyCorrection(Event e) {
        String cible = e.get("cible");
        if (cible == null) return;
        Depense d = depenses.get(cible);
        if (d != null) {
            if (e.get("montant_centimes") != null) d.montantCentimes = parseLong(e.get("montant_centimes"), d.montantCentimes);
            if (e.get("fournisseur") != null) d.fournisseur = e.get("fournisseur");
            if (e.get("date") != null) d.date = e.get("date");
            if (e.get("refacturer") != null) d.refacturer = e.get("refacturer");
            return;
        }
        Note n = notes.get(cible);
        if (n != null && e.get("texte") != null) n.texte = e.get("texte");
    }

    private static long parseLong(String s, long fallback) {
        if (s == null) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public List<Depense> depenses() {
        return new ArrayList<>(depenses.values());
    }

    public Depense depense(String id) {
        return depenses.get(id);
    }

    public Note note(String id) {
        return notes.get(id);
    }

    public long totalDepensesCentimes() {
        long total = 0;
        for (Depense d : depenses.values()) total += d.montantCentimes;
        return total;
    }

    /** Vrai si la dépense doit être refacturée au client, en tenant compte du mode du chantier. */
    public boolean estARefacturer(Depense d) {
        if ("oui".equals(d.refacturer)) return true;
        if ("non".equals(d.refacturer)) return false;
        return MODE_REEL.equals(mode);
    }

    /** Dépenses et notes, des plus récentes aux plus anciennes. */
    public List<Ajout> derniersAjouts() {
        List<Ajout> list = new ArrayList<>();
        for (Depense d : depenses.values()) list.add(new Ajout(Event.DEPENSE, d.id, d.creeLe));
        for (Note n : notes.values()) list.add(new Ajout(Event.NOTE, n.id, n.creeLe));
        list.sort((a, b) -> Long.compare(b.at, a.at));
        return list;
    }

    public static String libelleStatut(String statut) {
        switch (statut) {
            case EN_COURS: return "En cours";
            case TERMINE: return "Terminé";
            default: return "À démarrer";
        }
    }
}
