package com.gregory.truelle.data;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Une ligne du journal d'un chantier. Un événement n'est JAMAIS modifié après sa création :
 * une correction ou une suppression est un nouvel événement. C'est ce qui empêche
 * deux téléphones d'écraser le travail l'un de l'autre.
 */
public final class Event {

    public static final String CHANTIER_CREE = "chantier_cree";
    public static final String CHANTIER_MODIFIE = "chantier_modifie";
    public static final String STATUT = "statut";
    public static final String DEPENSE = "depense";
    public static final String CORRECTION = "correction";
    public static final String SUPPRESSION = "suppression";
    public static final String NOTE = "note";
    public static final String DEVIS = "devis";
    public static final String TRAVAUX = "travaux";
    public static final String FACTURATION = "facturation";
    /** Heures de main d'œuvre passées sur le chantier. */
    public static final String HEURES = "heures";
    /** Infos de l'entreprise (nom, adresse, SIRET…), dans le journal de _App/. */
    public static final String ENTREPRISE = "entreprise";

    public final String id;
    public final String type;
    public final String chantierId;
    public final long at;
    public final String auteur;
    private final Map<String, String> data;

    public Event(String id, String type, String chantierId, long at, String auteur, Map<String, String> data) {
        this.id = id;
        this.type = type;
        this.chantierId = chantierId;
        this.at = at;
        this.auteur = auteur;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public static Event create(String type, String chantierId, String auteur, Map<String, String> data) {
        return new Event(UUID.randomUUID().toString(), type, chantierId, System.currentTimeMillis(), auteur, data);
    }

    public String get(String key) {
        return data.get(key);
    }

    public String get(String key, String fallback) {
        String v = data.get(key);
        return v == null ? fallback : v;
    }

    public Map<String, String> data() {
        return data;
    }

    /** Nom du fichier dans journal/ : date, heure, type et début de l'identifiant. Trié naturellement par date. */
    public String fileName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.FRANCE).format(new Date(at));
        return stamp + "_" + type + "_" + (id.length() > 8 ? id.substring(0, 8) : id) + ".json";
    }

    public String toJson() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("chantier", chantierId);
        m.put("at", Long.toString(at));
        m.put("auteur", auteur);
        for (Map.Entry<String, String> e : data.entrySet()) {
            m.put("d." + e.getKey(), e.getValue());
        }
        return JsonLite.write(m);
    }

    public static Event fromJson(String json) {
        Map<String, String> m = JsonLite.read(json);
        Map<String, String> data = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (e.getKey().startsWith("d.")) data.put(e.getKey().substring(2), e.getValue());
        }
        String id = m.get("id");
        String type = m.get("type");
        if (id == null || type == null) throw new IllegalArgumentException("Événement sans identifiant ou sans type");
        long at = Long.parseLong(m.getOrDefault("at", "0"));
        return new Event(id, type, m.get("chantier"), at, m.getOrDefault("auteur", ""), data);
    }
}
