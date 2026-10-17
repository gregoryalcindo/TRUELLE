package com.gregory.truelle.ui.screens;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.DossierDepenses;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.RecapFin;
import com.gregory.truelle.ui.PdfFinChantier;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * « PDF du chantier », par catégorie :
 * 1. Récapitulatif client (à envoyer au client) ;
 * 2. Dossier des factures de dépenses (toutes les factures, par catégorie, pour toi ou le comptable).
 */
public final class FinChantierScreen extends Screen {

    private static final String DOSSIER_CLIENT = "PDF client";
    private static final String DOSSIER_FACTURES = "Factures des dépenses";

    private final String chantierId;
    private boolean joindreFactures = true;
    private String enCours; // dossier du PDF en cours de création, ou null

    public FinChantierScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Retour", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "PDF du chantier", 30));
        if (c == null) return p.racine;

        Map<String, String> entreprise = store().entreprise();
        String nom = entreprise.get("nom");
        if (nom == null || nom.trim().isEmpty()) {
            LinearLayout alerte = carte();
            alerte.addView(UiKit.text(a, "L'en-tête des PDF est vide : renseignez le nom et les coordonnées de l'entreprise.", 16, UiKit.ENCRE, true));
            alerte.addView(UiKit.boutonContour(a, "Renseigner l'entreprise", v -> a.ouvrir(new AppReglagesScreen(a))), UiKit.pleineLargeur());
            p.contenu.addView(alerte, UiKit.pleineLargeur());
        }

        p.contenu.addView(blocRecap(c, entreprise), UiKit.pleineLargeur());
        p.contenu.addView(blocFactures(c, entreprise), UiKit.pleineLargeur());
        return p.racine;
    }

    // ------------------------------------------------------------ 1. récapitulatif client

    private View blocRecap(Chantier c, Map<String, String> entreprise) {
        RecapFin recap = RecapFin.construire(c, joindreFactures);
        LinearLayout bloc = carte();
        bloc.addView(UiKit.titre(a, "1. Récapitulatif client", 22));
        bloc.addView(UiKit.text(a, "À envoyer au client. Accompagne votre facture, ne la remplace pas.", 15, UiKit.ENCRE_2, false));
        for (RecapFin.Section s : recap.sections) {
            bloc.addView(UiKit.ligneChiffre(a, s.titre + " (" + s.lignes.size() + ")", Money.format(s.total), false), UiKit.pleineLargeur());
        }
        if (recap.sections.isEmpty()) {
            bloc.addView(UiKit.text(a, "Rien à récapituler : devis signés, travaux en plus acceptés ou matériel refacturé.", 16, UiKit.ENCRE_2, false));
        }
        bloc.addView(UiKit.ligneChiffre(a, "Total TTC", Money.format(recap.totalTtc), true), UiKit.pleineLargeur());
        bloc.addView(UiKit.ligneChiffre(a, "Déjà réglé", Money.format(recap.dejaRegle), false), UiKit.pleineLargeur());
        bloc.addView(UiKit.ligneChiffre(a, "Reste à payer", Money.format(recap.resteAPayer()), true), UiKit.pleineLargeur());

        RecapFin avec = RecapFin.construire(c, true);
        if (!avec.annexes.isEmpty()) {
            bloc.addView(UiKit.text(a, "Factures du matériel refacturé en annexe (" + avec.annexes.size() + ")", 16, UiKit.ENCRE_2, false));
            bloc.addView(UiKit.choix(a, new String[][]{{"oui", "Les joindre"}, {"non", "Ne pas joindre"}}, joindreFactures ? "oui" : "non", choisi -> {
                joindreFactures = "oui".equals(choisi);
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        } else if (Chantier.MODE_FORFAIT.equals(c.mode)) {
            bloc.addView(UiKit.text(a, "Au forfait : aucune facture fournisseur jointe, vos prix d'achat restent confidentiels.", 15, UiKit.ENCRE_2, false));
        }

        boolean occupe = DOSSIER_CLIENT.equals(enCours);
        android.widget.Button creer = UiKit.boutonAction(a, occupe ? "Création…" : "Créer le récapitulatif", v ->
                creer(DOSSIER_CLIENT, "recapitulatif-" + c.titre, () -> PdfFinChantier.generer(a.getCacheDir(), store(), c, RecapFin.construire(c, joindreFactures), entreprise)));
        creer.setEnabled(enCours == null);
        bloc.addView(creer, UiKit.pleineLargeur());
        historique(bloc, c, DOSSIER_CLIENT, "Récapitulatif de fin de chantier – ");
        return bloc;
    }

    // ------------------------------------------------------------ 2. dossier des factures de dépenses

    private View blocFactures(Chantier c, Map<String, String> entreprise) {
        DossierDepenses d = DossierDepenses.construire(c);
        LinearLayout bloc = carte();
        bloc.addView(UiKit.titre(a, "2. Factures des dépenses", 22));
        bloc.addView(UiKit.text(a, "Toutes les factures de dépenses du chantier, rangées par catégorie. Pour vous ou le comptable.", 15, UiKit.ENCRE_2, false));
        for (com.gregory.truelle.data.RecapFin.Section s : d.sections) {
            bloc.addView(UiKit.ligneChiffre(a, s.titre + " (" + s.lignes.size() + ")", Money.format(s.total), false), UiKit.pleineLargeur());
        }
        if (d.sections.isEmpty()) bloc.addView(UiKit.text(a, "Aucune dépense sur ce chantier.", 16, UiKit.ENCRE_2, false));
        bloc.addView(UiKit.ligneChiffre(a, "Total des dépenses", Money.format(d.total), true), UiKit.pleineLargeur());
        bloc.addView(UiKit.text(a, d.annexes.size() + " facture(s) jointe(s)" + (d.sansFacture > 0 ? " · " + d.sansFacture + " dépense(s) sans facture" : ""), 16, UiKit.ENCRE_2, false));

        boolean occupe = DOSSIER_FACTURES.equals(enCours);
        android.widget.Button creer = UiKit.boutonAction(a, occupe ? "Création…" : "Créer le dossier des factures", v ->
                creer(DOSSIER_FACTURES, "factures-depenses-" + c.titre, () -> PdfFinChantier.genererDossierDepenses(a.getCacheDir(), store(), c, DossierDepenses.construire(c), entreprise)));
        creer.setEnabled(enCours == null && !d.sections.isEmpty());
        bloc.addView(creer, UiKit.pleineLargeur());
        historique(bloc, c, DOSSIER_FACTURES, "Factures des dépenses – ");
        return bloc;
    }

    // ------------------------------------------------------------ commun

    private interface Fabrique {
        File fabriquer() throws Exception;
    }

    private void creer(String dossier, String libelle, Fabrique fabrique) {
        if (enCours != null) return;
        enCours = dossier;
        a.remplacer(this);
        new Thread(() -> {
            String erreur = null;
            File range = null;
            try {
                File tmp = fabrique.fabriquer();
                String chemin = store().importerFichier(chantierId, dossier, tmp, libelle, "pdf");
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                range = store().fichier(chantierId, chemin);
            } catch (Exception e) {
                erreur = e.getMessage();
            }
            final String err = erreur;
            final File pdf = range;
            a.runOnUiThread(() -> {
                enCours = null;
                if (err != null) {
                    toast("PDF non créé : " + err);
                } else {
                    toast("PDF créé ✓");
                    a.apresEnregistrement();
                    if (pdf != null) Scanner.ouvrir(a, pdf);
                }
                a.remplacer(this);
            });
        }).start();
    }

    private void historique(LinearLayout bloc, Chantier c, String dossier, String objet) {
        List<File> pdfs = store().fichiers(chantierId, dossier);
        if (pdfs.isEmpty()) return;
        bloc.addView(UiKit.text(a, "Déjà créés", 16, UiKit.ENCRE, true));
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE);
        for (File pdf : pdfs) {
            LinearLayout ligne = UiKit.colonne(a, 6);
            ligne.addView(UiKit.text(a, "Créé le " + f.format(new Date(pdf.lastModified())), 16, UiKit.ENCRE_2, false));
            LinearLayout boutons = UiKit.ligne(a, 8);
            boutons.addView(UiKit.boutonContour(a, "Voir", v -> Scanner.ouvrir(a, pdf)), UiKit.poids(1));
            boutons.addView(UiKit.boutonContour(a, "Envoyer", v -> envoyer(objet + c.titre, pdf)), UiKit.poids(1));
            ligne.addView(boutons, UiKit.pleineLargeur());
            bloc.addView(ligne, UiKit.pleineLargeur());
        }
    }

    /** Partage le PDF (mail, WhatsApp, SMS…) avec un objet prêt. */
    private void envoyer(String objet, File pdf) {
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fichiers", pdf);
            Intent i = new Intent(Intent.ACTION_SEND)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, objet)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Envoyer le PDF"));
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            toast("Envoi impossible : " + e.getMessage());
        }
    }

    private LinearLayout carte() {
        LinearLayout l = UiKit.carte(a, 10);
        l.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        return l;
    }
}
