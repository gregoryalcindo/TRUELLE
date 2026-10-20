package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Toast;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.LocalStore;
import com.gregory.truelle.sync.SyncManager;
import com.gregory.truelle.ui.Images;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Place occupée sur ce téléphone, et « Libérer l'espace des chantiers terminés » :
 * les photos et les factures déjà copiées sur le Drive quittent le téléphone (les miniatures restent).
 * Rien n'est jamais supprimé du Drive.
 */
public final class StockageScreen extends Screen {

    /** Dossiers dont les fichiers peuvent quitter le téléphone (jamais le journal ni les miniatures). */
    static final String[] DOSSIERS_LIBERABLES = {Images.DOSSIER_PHOTOS, "Dépenses"};

    public StockageScreen(MainActivity a) {
        super(a);
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Outils", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Place sur ce téléphone", 30));

        List<Chantier> tous = store().chantiers();
        long total = 0, liberable = 0;
        Blocs.Groupe g = new Blocs.Groupe(a);
        for (Chantier c : tous) {
            long t = store().taille(c.id);
            total += t;
            boolean termine = Chantier.TERMINE.equals(c.statut);
            if (termine) liberable += liberable(c.id);
            g.ajouter(Blocs.rangee(a, null, c.titre, Chantier.libelleStatut(c.statut), taille(t), false, null));
        }
        p.contenu.addView(UiKit.ligneChiffre(a, "Total des chantiers", taille(total), true), UiKit.pleineLargeur());
        if (!g.vide()) p.contenu.addView(g.vue, UiKit.pleineLargeur());
        p.contenu.addView(UiKit.text(a, "Les photos sont réduites à environ 300 Ko et les factures scannées sont déjà compressées. "
                + "Tout est gardé sur le Drive : sur le téléphone, on peut ne garder que les chantiers en cours.", 15, UiKit.ENCRE_2, false));

        SyncManager sync = a.sync();
        boolean pret = sync.connecte() && sync.derniereSynchro() > 0;
        if (!pret) {
            p.contenu.addView(UiKit.text(a, "Connectez Google Drive (Outils → Entreprise) pour pouvoir libérer de la place sans rien perdre.", 15, UiKit.AMBRE, true));
        }
        long finalLiberable = liberable;
        p.ajouterAuPied(UiKit.boutonAction(a, liberable > 0 ? "Libérer " + taille(liberable) + " (chantiers terminés)" : "Rien à libérer pour l'instant", v -> {
            if (!pret || finalLiberable <= 0) return;
            new AlertDialog.Builder(a)
                    .setTitle("Libérer " + taille(finalLiberable) + " ?")
                    .setMessage("Les photos et les factures des chantiers terminés restent sur le Drive. Sur ce téléphone, il restera les miniatures ; "
                            + "chaque fichier se retélécharge quand on l'ouvre.")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Libérer", (d, w) -> liberer())
                    .show();
        }));
        return p.racine;
    }

    /** Octets libérables d'un chantier : fichiers déjà envoyés (antérieurs à la dernière synchro réussie). */
    private long liberable(String chantierId) {
        long t = 0;
        long derniere = a.sync().derniereSynchro();
        for (String dossier : DOSSIERS_LIBERABLES) {
            for (File f : store().fichiers(chantierId, dossier)) if (f.lastModified() < derniere) t += f.length();
        }
        return t;
    }

    private void liberer() {
        long derniere = a.sync().derniereSynchro();
        long libere = 0;
        int erreurs = 0;
        LocalStore s = store();
        for (Chantier c : s.chantiers()) {
            if (!Chantier.TERMINE.equals(c.statut)) continue;
            for (String dossier : DOSSIERS_LIBERABLES) {
                for (File f : s.fichiers(c.id, dossier)) {
                    if (f.lastModified() >= derniere) continue; // peut-être pas encore sur le Drive : on garde
                    try {
                        libere += s.liberer(c.id, dossier + "/" + f.getName());
                    } catch (Exception e) {
                        erreurs++;
                    }
                }
            }
        }
        a.confirmer(taille(libere) + " libérés ✓");
        if (erreurs > 0) Toast.makeText(a, erreurs + " fichier(s) n'ont pas pu être retirés", Toast.LENGTH_LONG).show();
        a.remplacer(this);
    }

    static String taille(long octets) {
        if (octets < 1024 * 1024) return Math.max(0, Math.round(octets / 1024.0)) + " Ko";
        if (octets < 1024L * 1024 * 1024) return String.format(Locale.FRANCE, "%.0f Mo", octets / (1024.0 * 1024));
        return String.format(Locale.FRANCE, "%.1f Go", octets / (1024.0 * 1024 * 1024));
    }

    /**
     * Rapatrie depuis le Drive les fichiers d'un chantier laissés là-bas (avant de créer un PDF, par exemple).
     * {@code fin} est appelé sur l'écran, avec le nombre de fichiers qui n'ont pas pu revenir.
     */
    static void recupererTout(MainActivity a, String chantierId, List<String> chemins, java.util.function.IntConsumer fin) {
        List<String> restants = new ArrayList<>(chemins);
        int[] echecs = {0};
        Runnable[] suivant = new Runnable[1];
        suivant[0] = () -> {
            if (restants.isEmpty()) {
                fin.accept(echecs[0]);
                return;
            }
            String chemin = restants.remove(0);
            File dest = a.store().emplacement(chantierId, chemin);
            a.sync().recuperer(a.store().cheminDepuisRacine(chantierId, chemin), dest, (ok, msg) -> {
                if (ok) {
                    try {
                        a.store().recupere(chantierId, chemin);
                    } catch (Exception ignored) {
                        // la liste sera corrigée au prochain essai
                    }
                } else echecs[0]++;
                suivant[0].run();
            });
        };
        suivant[0].run();
    }
}
