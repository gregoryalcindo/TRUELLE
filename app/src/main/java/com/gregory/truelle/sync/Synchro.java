package com.gregory.truelle.sync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronise le dossier local « Chantiers » avec le dossier distant du même nom.
 *
 * Principe : les fichiers ne sont jamais modifiés après leur création (journal sans écrasement,
 * factures et signatures aux noms uniques). Il suffit donc de comparer les NOMS :
 * ce qui manque d'un côté est copié depuis l'autre. Rien n'est jamais supprimé ni écrasé.
 */
public final class Synchro {

    public static final class Bilan {
        public int envoyes;
        public int recus;

        @Override
        public String toString() {
            return envoyes + " envoyé(s), " + recus + " reçu(s)";
        }
    }

    private final Distant distant;
    private final Bilan bilan = new Bilan();

    private Synchro(Distant distant) {
        this.distant = distant;
    }

    public static Bilan synchroniser(File racineLocale, Distant distant) throws IOException {
        Synchro s = new Synchro(distant);
        if (!racineLocale.exists() && !racineLocale.mkdirs()) throw new IOException("Dossier local inaccessible");
        s.miroir(racineLocale, Collections.singletonList(distant.racine()));
        return s.bilan;
    }

    /**
     * @param distants les dossiers distants portant ce nom (en principe un seul ; plusieurs si
     *                 les deux téléphones l'ont créé en même temps : on lit tout, on écrit dans le premier).
     */
    private void miroir(File local, List<String> distants) throws IOException {
        String principal = distants.get(0);
        Map<String, Distant.Element> fichiersDistants = new LinkedHashMap<>();
        Map<String, List<String>> dossiersDistants = new LinkedHashMap<>();
        for (String id : distants) {
            for (Distant.Element e : distant.lister(id)) {
                if (ignorer(e.nom)) continue;
                if (e.dossier) {
                    List<String> ids = dossiersDistants.get(e.nom);
                    if (ids == null) {
                        ids = new ArrayList<>();
                        dossiersDistants.put(e.nom, ids);
                    }
                    ids.add(e.id);
                } else if (!fichiersDistants.containsKey(e.nom)) {
                    fichiersDistants.put(e.nom, e);
                }
            }
        }

        File[] locaux = local.listFiles();
        List<File> fichiersLocaux = new ArrayList<>();
        List<File> dossiersLocaux = new ArrayList<>();
        if (locaux != null) {
            for (File f : locaux) {
                if (ignorer(f.getName())) continue;
                if (f.isDirectory()) dossiersLocaux.add(f);
                else fichiersLocaux.add(f);
            }
        }

        // 1. Envoyer ce qui manque là-bas.
        for (File f : fichiersLocaux) {
            if (!fichiersDistants.containsKey(f.getName())) {
                distant.envoyer(principal, f);
                bilan.envoyes++;
            }
        }
        // 2. Recevoir ce qui manque ici (via un fichier temporaire : jamais de fichier à moitié reçu).
        for (Distant.Element e : fichiersDistants.values()) {
            File dest = new File(local, e.nom);
            if (dest.exists()) continue;
            File tmp = new File(local, "." + e.nom + ".tmp");
            distant.telecharger(e.id, tmp);
            if (!tmp.renameTo(dest)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("Impossible d'enregistrer " + e.nom);
            }
            bilan.recus++;
        }
        // 3. Descendre dans les sous-dossiers des deux côtés.
        for (File d : dossiersLocaux) {
            List<String> ids = dossiersDistants.remove(d.getName());
            if (ids == null) ids = Collections.singletonList(distant.creerDossier(principal, d.getName()));
            miroir(d, ids);
        }
        for (Map.Entry<String, List<String>> e : dossiersDistants.entrySet()) {
            File d = new File(local, e.getKey());
            if (!d.exists() && !d.mkdirs()) throw new IOException("Impossible de créer " + e.getKey());
            miroir(d, e.getValue());
        }
    }

    /** Fichiers techniques jamais synchronisés : cachés et temporaires. */
    static boolean ignorer(String nom) {
        return nom.startsWith(".") || nom.endsWith(".tmp");
    }
}
