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

    public static final String TYPE_PRO = "pro";
    /** Travaux entre proches : fonctionnement identique au pro, sans aucune TVA (chantier non déclaré). */
    public static final String TYPE_PROCHES = "proches";

    public static final String MODE_REEL = "reel";
    public static final String MODE_FORFAIT = "forfait";

    public final String id;
    public String dossier = "";
    public String titre = "";
    public String client = "";
    public String adresse = "";
    public String mode = MODE_FORFAIT;
    public int majorationPct = 0;
    /** Budget matériel prévu (centimes), 0 si non renseigné. */
    public long budgetMaterielCentimes = 0;
    /** TVA par défaut du chantier (points de base), -1 = celle de l'entreprise. */
    public int tvaDefautBp = -1;
    /** Retenue de garantie (points de base, 0 à 500 = 5 %). */
    public int retenueBp = 0;
    public String type = TYPE_PRO;
    /** Téléphone du client (pour l'appeler depuis la fiche). */
    public String telephone = "";
    /** Identifiant de la photo de couverture ("" = la plus récente). */
    public String couverture = "";
    /** Vrai si nous sommes sous-traitant d'une entreprise du bâtiment : factures hors taxes (autoliquidation). */
    public boolean autoliquidation;

    // ---- La base simple d'un chantier (les tiroirs plus détaillés sont facultatifs)
    /** Prix convenu avec le client, TTC (0 = pas encore). Ignoré dès qu'un devis est signé. */
    public long prixCentimes = 0;
    /** TVA du prix simple (points de base), -1 = celle du chantier. */
    public int prixTvaBp = -1;
    /** « Je prévois environ … » : coût total prévu, en un seul montant. */
    public long coutPrevuCentimes = 0;
    /** Coût prévu réparti par poste (tiroir « Répartir »), en plus des coûts des lignes de devis. */
    public final java.util.Map<String, Long> prevuPostes = new java.util.LinkedHashMap<>();
    /** « oui » quand le client a signé la mention de TVA réduite pour le prix simple. */
    public String certification = "";
    /** Catégories de dépenses refacturées au client (choix de fin de chantier) ; null = selon le mode. */
    public java.util.Set<String> refacturerCategories = null;
    /** Avancement des travaux déclaré (points de base, 0 à 10000), -1 = non renseigné. */
    public int avancementBp = -1;
    public String statut = A_DEMARRER;
    public long creeLe;
    public long modifieLe;

    private final Map<String, Depense> depenses = new LinkedHashMap<>();
    private final Map<String, Note> notes = new LinkedHashMap<>();
    private final Map<String, Element> elements = new LinkedHashMap<>();
    private final Map<String, Heure> heures = new LinkedHashMap<>();

    // Statuts des devis, travaux en plus et lignes de facturation.
    public static final String DEVIS_NOUS = "nous";
    public static final String DEVIS_SOUS_TRAITANT = "sous_traitant";
    public static final String DEVIS_ENVOYE = "envoye";      // nos devis : envoyé au client
    public static final String DEVIS_RECU = "recu";          // sous-traitant : reçu
    public static final String DEVIS_SIGNE = "signe";        // accepté (par le client, ou par nous pour un sous-traitant)
    public static final String DEVIS_REFUSE = "refuse";
    public static final String DEVIS_BROUILLON = "brouillon";
    /** Remplacé par une nouvelle version (prix modifiés après signature). */
    public static final String DEVIS_REMPLACE = "remplace";
    public static final String TRAVAUX_PROPOSE = "propose";
    public static final String TRAVAUX_ACCEPTE = "accepte";
    public static final String TRAVAUX_FAIT = "fait";
    public static final String FACT_A_FACTURER = "a_facturer";
    public static final String FACT_FACTURE = "facture";
    public static final String FACT_ENCAISSE = "encaisse";

    /**
     * Devis, travaux en plus, ligne de facturation : un élément à champs libres.
     * Une correction remplace simplement les champs qu'elle contient.
     */
    public static final class Element {
        public final String id;
        public final String type;
        public final String auteur;
        public final long creeLe;
        private final Map<String, String> champs = new LinkedHashMap<>();

        Element(String id, String type, String auteur, long creeLe, Map<String, String> data) {
            this.id = id;
            this.type = type;
            this.auteur = auteur;
            this.creeLe = creeLe;
            champs.putAll(data);
        }

        public String get(String cle) {
            String v = champs.get(cle);
            return v == null ? "" : v;
        }

        public long centimes(String cle) {
            return parseLong(champs.get(cle), 0);
        }

        /** Élément non enregistré (aperçu, calculs). */
        public static Element temporaire(Map<String, String> data) {
            return new Element("tmp", "", "", 0, data);
        }

        void appliquer(Map<String, String> corrections) {
            for (Map.Entry<String, String> e : corrections.entrySet()) {
                if (!"cible".equals(e.getKey())) champs.put(e.getKey(), e.getValue());
            }
        }
    }

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
        /** Chemin de la facture dans le dossier du chantier (ex. « Dépenses/2026…pdf »), vide si aucune. */
        public String fichier = "";
        /** Catégorie (voir Categories) : matériel par défaut. */
        public String categorie = Categories.MATERIEL;
        /** TVA lue sur la facture, -1 si inconnue. */
        public long tvaCentimes = -1;
        /** Taux de TVA choisi (points de base), -1 = non précisé (ancienne dépense). */
        public int tvaBp = -1;
        /** Faux tant qu'elle n'a pas été vérifiée (saisie terrain, lecture automatique). */
        public boolean valide = true;
        public final String auteur;
        public final long creeLe;

        Depense(String id, String auteur, long creeLe) {
            this.id = id;
            this.auteur = auteur;
            this.creeLe = creeLe;
        }
    }

    /** Heures de main d'œuvre. */
    public static final class Heure {
        public final String id;
        public String date = "";
        public String personne = "";
        /** Heures en centièmes (7 h 30 = 750). */
        public long heuresCentiemes;
        /** Coût horaire chargé de cette saisie, -1 = celui des réglages. */
        public long tauxCentimes = -1;
        public String note = "";
        public final String auteur;
        public final long creeLe;

        Heure(String id, String auteur, long creeLe) {
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
                d.fichier = e.get("fichier", "");
                d.categorie = Categories.normaliser(e.get("categorie"));
                d.tvaCentimes = parseLong(e.get("tva_centimes"), -1);
                d.tvaBp = (int) parseLong(e.get("tva_bp"), -1);
                d.valide = !"non".equals(e.get("valide"));
                depenses.put(e.id, d);
                break;
            }
            case Event.HEURES: {
                Heure h = new Heure(e.id, e.auteur, e.at);
                appliquerHeure(h, e);
                heures.put(e.id, h);
                break;
            }
            case Event.NOTE:
                notes.put(e.id, new Note(e.id, e.get("texte", ""), e.auteur, e.at));
                break;
            case Event.DEVIS:
            case Event.TRAVAUX:
            case Event.FACTURATION:
            case Event.TACHE:
            case Event.PHOTO:
                elements.put(e.id, new Element(e.id, e.type, e.auteur, e.at, e.data()));
                break;
            case Event.CORRECTION:
                applyCorrection(e);
                break;
            case Event.SUPPRESSION: {
                String cible = e.get("cible");
                if (cible != null) {
                    depenses.remove(cible);
                    notes.remove(cible);
                    elements.remove(cible);
                    heures.remove(cible);
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
        if (e.get("telephone") != null) telephone = e.get("telephone");
        if (e.get("couverture") != null) couverture = e.get("couverture");
        if (e.get("autoliquidation") != null) autoliquidation = "oui".equals(e.get("autoliquidation"));
        if (e.get("prix_centimes") != null) prixCentimes = Math.max(0, parseLong(e.get("prix_centimes"), 0));
        if (e.get("prix_tva_bp") != null) prixTvaBp = (int) parseLong(e.get("prix_tva_bp"), -1);
        if (e.get("cout_prevu_centimes") != null) coutPrevuCentimes = Math.max(0, parseLong(e.get("cout_prevu_centimes"), 0));
        if (e.get("certification") != null) certification = e.get("certification");
        for (String[] p : Postes.TOUS) {
            String v = e.get("prevu_" + p[0] + "_centimes");
            if (v != null) prevuPostes.put(p[0], Math.max(0, parseLong(v, 0)));
        }
        if (e.get("refacturer_categories") != null) {
            refacturerCategories = new java.util.LinkedHashSet<>();
            for (String x : e.get("refacturer_categories").split(",")) if (!x.trim().isEmpty() && !"aucune".equals(x.trim())) refacturerCategories.add(x.trim());
        }
        if (e.get("client") != null) client = e.get("client");
        if (e.get("adresse") != null) adresse = e.get("adresse");
        if (e.get("mode") != null) mode = MODE_REEL.equals(e.get("mode")) ? MODE_REEL : MODE_FORFAIT;
        if (e.get("majoration_pct") != null) majorationPct = (int) parseLong(e.get("majoration_pct"), 0);
        if (e.get("budget_materiel_centimes") != null) budgetMaterielCentimes = parseLong(e.get("budget_materiel_centimes"), 0);
        if (e.get("tva_defaut_bp") != null) tvaDefautBp = (int) parseLong(e.get("tva_defaut_bp"), -1);
        if (e.get("type") != null) type = TYPE_PROCHES.equals(e.get("type")) ? TYPE_PROCHES : TYPE_PRO;
        if (e.get("avancement_bp") != null) avancementBp = (int) Math.max(0, Math.min(10000, parseLong(e.get("avancement_bp"), 0)));
        if (e.get("retenue_bp") != null) retenueBp = (int) Math.max(0, Math.min(500, parseLong(e.get("retenue_bp"), 0)));
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
            if (e.get("fichier") != null) d.fichier = e.get("fichier");
            if (e.get("categorie") != null) d.categorie = Categories.normaliser(e.get("categorie"));
            if (e.get("tva_centimes") != null) d.tvaCentimes = parseLong(e.get("tva_centimes"), d.tvaCentimes);
            if (e.get("tva_bp") != null) d.tvaBp = (int) parseLong(e.get("tva_bp"), d.tvaBp);
            if (e.get("valide") != null) d.valide = !"non".equals(e.get("valide"));
            return;
        }
        Heure h = heures.get(cible);
        if (h != null) {
            appliquerHeure(h, e);
            return;
        }
        Note n = notes.get(cible);
        if (n != null && e.get("texte") != null) n.texte = e.get("texte");
        Element el = elements.get(cible);
        if (el != null) el.appliquer(e.data());
    }

    private static void appliquerHeure(Heure h, Event e) {
        if (e.get("date") != null) h.date = e.get("date");
        if (e.get("personne") != null) h.personne = e.get("personne");
        if (e.get("heures_centiemes") != null) h.heuresCentiemes = parseLong(e.get("heures_centiemes"), h.heuresCentiemes);
        if (e.get("taux_centimes") != null) h.tauxCentimes = parseLong(e.get("taux_centimes"), h.tauxCentimes);
        if (e.get("note") != null) h.note = e.get("note");
    }

    static long parseLong(String s, long fallback) {
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

    public List<Heure> heures() {
        List<Heure> l = new ArrayList<>(heures.values());
        l.sort((a, b) -> Long.compare(b.creeLe, a.creeLe));
        return l;
    }

    public Heure heure(String id) {
        return heures.get(id);
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
    // ------------------------------------------------------------- tâches et photos

    public static final String MOMENT_AVANT = "avant";
    public static final String MOMENT_PENDANT = "pendant";
    public static final String MOMENT_APRES = "apres";

    public static boolean tacheFaite(Element t) {
        return "oui".equals(t.get("fait"));
    }

    /** Tâches : à faire d'abord (par date, sans date à la fin), puis faites (les plus récentes d'abord). */
    public List<Element> taches() {
        List<Element> l = elements(Event.TACHE);
        l.sort((x, y) -> {
            boolean fx = tacheFaite(x), fy = tacheFaite(y);
            if (fx != fy) return fx ? 1 : -1;
            if (fx) return Long.compare(y.creeLe, x.creeLe);
            int cmp = Long.compare(Dates.cle(x.get("date")), Dates.cle(y.get("date")));
            return cmp != 0 ? cmp : Long.compare(x.creeLe, y.creeLe);
        });
        return l;
    }

    public int tachesFaites() {
        int n = 0;
        for (Element t : elements(Event.TACHE)) if (tacheFaite(t)) n++;
        return n;
    }

    /** Photos, les plus récentes d'abord. */
    public List<Element> photos() {
        List<Element> l = elements(Event.PHOTO);
        l.sort((x, y) -> Long.compare(y.creeLe, x.creeLe));
        return l;
    }

    /** Photo de couverture : celle choisie, sinon la plus récente ; null sans photo. */
    public Element photoCouverture() {
        List<Element> l = photos();
        for (Element p : l) if (p.id.equals(couverture)) return p;
        return l.isEmpty() ? null : l.get(0);
    }

    /** Moment proposé pour une nouvelle photo, selon le statut du chantier. */
    public String momentParDefaut() {
        if (A_DEMARRER.equals(statut)) return MOMENT_AVANT;
        if (TERMINE.equals(statut)) return MOMENT_APRES;
        return MOMENT_PENDANT;
    }

    public boolean entreProches() {
        return TYPE_PROCHES.equals(type);
    }

    public boolean estARefacturer(Depense d) {
        if ("oui".equals(d.refacturer)) return true;
        if ("non".equals(d.refacturer)) return false;
        if (refacturerCategories != null) return refacturerCategories.contains(d.categorie);
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

    // ------------------------------------------------------------ devis, travaux, facturation

    public Element element(String id) {
        return elements.get(id);
    }

    /** Éléments d'un type donné, du plus ancien au plus récent. */
    public List<Element> elements(String type) {
        List<Element> l = new ArrayList<>();
        for (Element el : elements.values()) if (el.type.equals(type)) l.add(el);
        return l;
    }

    // ------------------------------------------------------------ rentabilité

    /** Total des dépenses d'une catégorie. */
    public long totalCategorie(String categorie) {
        long t = 0;
        for (Depense d : depenses.values()) if (d.categorie.equals(categorie)) t += d.montantCentimes;
        return t;
    }

    public static String libelleStatut(String statut) {
        switch (statut) {
            case EN_COURS: return "En cours";
            case TERMINE: return "Terminé";
            default: return "À démarrer";
        }
    }
}
