package com.gregory.truelle.sync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronise le dossier local « Chantiers » avec le dossier distant du même nom.
 *
 * Principe : les fichiers ne sont jamais modifiés après leur création (journal sans écrasement,
 * factures et signatures aux noms uniques). Il suffit donc de comparer les NOMS :
 * ce qui manque d'un côté est copié depuis l'autre. Rien n'est jamais supprimé ni écrasé.
 *
 * Place libérée sur le téléphone : un fichier déjà sur le Drive peut être retiré du téléphone.
 * Son nom est alors noté dans le fichier caché « .distants » du dossier, pour ne pas le
 * retélécharger ; {@link #recuperer} le rapatrie à la demande.
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

    /** Fichier caché (jamais synchronisé) listant les fichiers laissés sur le Drive. */
    public static final String DISTANTS = ".distants";

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
        // 2. Recevoir ce qui manque ici (via un fichier temporaire : jamais de fichier à moitié reçu),
        //    sauf ce qui a été volontairement laissé sur le Drive pour libérer de la place.
        Set<String> laisses = lireDistants(local);
        for (Distant.Element e : fichiersDistants.values()) {
            File dest = new File(local, e.nom);
            if (dest.exists() || laisses.contains(e.nom)) continue;
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

    /**
     * Rapatrie un seul fichier laissé sur le Drive.
     * @param chemin chemin depuis la racine « Chantiers » (ex. « 001 Salle de bain/Photos/photo.jpg »)
     * @return vrai si le fichier a été trouvé et copié dans {@code dest}
     */
    public static boolean recuperer(Distant distant, String chemin, File dest) throws IOException {
        String[] parties = chemin.split("/");
        List<String> dossiers = Collections.singletonList(distant.racine());
        for (int i = 0; i < parties.length; i++) {
            boolean dernier = i == parties.length - 1;
            List<String> suivants = new ArrayList<>();
            for (String id : dossiers) {
                for (Distant.Element e : distant.lister(id)) {
                    if (!e.nom.equals(parties[i]) || e.dossier == dernier) continue;
                    if (dernier) {
                        File tmp = new File(dest.getParentFile(), "." + dest.getName() + ".tmp");
                        distant.telecharger(e.id, tmp);
                        if (!tmp.renameTo(dest)) {
                            //noinspection ResultOfMethodCallIgnored
                            tmp.delete();
                            throw new IOException("Impossible d'enregistrer " + dest.getName());
                        }
                        return true;
                    }
                    suivants.add(e.id);
                }
            }
            if (suivants.isEmpty()) return false;
            dossiers = suivants;
        }
        return false;
    }

    /** Noms des fichiers de ce dossier laissés sur le Drive. */
    public static Set<String> lireDistants(File dossier) {
        Set<String> res = new HashSet<>();
        File f = new File(dossier, DISTANTS);
        if (!f.exists()) return res;
        try {
            for (String l : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                if (!l.trim().isEmpty()) res.add(l.trim());
            }
        } catch (IOException ignored) {
            // Liste illisible : on retéléchargera, rien n'est perdu.
        }
        return res;
    }

    /** Fichiers techniques jamais synchronisés : cachés et temporaires. */
    static boolean ignorer(String nom) {
        return nom.startsWith(".") || nom.endsWith(".tmp");
    }
}
