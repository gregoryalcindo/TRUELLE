package com.gregory.truelle.ui.screens;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
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

/** Lot 5 : PDF de fin de chantier à envoyer au client (récapitulatif + factures en annexe). */
public final class FinChantierScreen extends Screen {

    private final String chantierId;
    private boolean joindreFactures = true;
    private boolean enCours;

    public FinChantierScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Gérer le chantier", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "PDF de fin de chantier", 30));
        if (c == null) return p.racine;

        Map<String, String> entreprise = store().entreprise();
        String nom = entreprise.get("nom");
        if (nom == null || nom.trim().isEmpty()) {
            LinearLayout alerte = carte();
            alerte.addView(UiKit.text(a, "L'en-tête du PDF est vide : renseignez le nom et les coordonnées de l'entreprise.", 16, UiKit.ENCRE, true));
            alerte.addView(UiKit.boutonContour(a, "Renseigner l'entreprise", v -> a.ouvrir(new AppReglagesScreen(a))), UiKit.pleineLargeur());
            p.contenu.addView(alerte, UiKit.pleineLargeur());
        }

        RecapFin recap = RecapFin.construire(c, joindreFactures);

        // Aperçu du contenu.
        LinearLayout contenu = carte();
        contenu.addView(UiKit.titre(a, "Contenu du PDF", 20));
        for (RecapFin.Section s : recap.sections) {
            contenu.addView(UiKit.ligneChiffre(a, s.titre + " (" + s.lignes.size() + ")", Money.format(s.total), false), UiKit.pleineLargeur());
        }
        if (recap.sections.isEmpty()) {
            contenu.addView(UiKit.text(a, "Rien à récapituler pour l'instant : devis signés, travaux en plus acceptés ou matériel refacturé.", 16, UiKit.ENCRE_2, false));
        }
        contenu.addView(UiKit.ligneChiffre(a, "Total TTC", Money.format(recap.totalTtc), true), UiKit.pleineLargeur());
        contenu.addView(UiKit.ligneChiffre(a, "Déjà réglé", Money.format(recap.dejaRegle), false), UiKit.pleineLargeur());
        contenu.addView(UiKit.ligneChiffre(a, "Reste à payer", Money.format(recap.resteAPayer()), true), UiKit.pleineLargeur());
        if (!recap.signatures.isEmpty()) {
            contenu.addView(UiKit.text(a, recap.signatures.size() + " accord(s) signé(s) pour les travaux en plus", 16, UiKit.ENCRE_2, false));
        }
        p.contenu.addView(contenu, UiKit.pleineLargeur());

        // Factures fournisseurs en annexe (seulement le matériel refacturé).
        RecapFin avec = RecapFin.construire(c, true);
        if (!avec.annexes.isEmpty()) {
            LinearLayout bloc = UiKit.colonne(a, 8);
            bloc.addView(UiKit.text(a, "Factures fournisseurs en annexe (" + avec.annexes.size() + ")", 16, UiKit.ENCRE_2, false));
            bloc.addView(UiKit.choix(a, new String[][]{{"oui", "Les joindre"}, {"non", "Ne pas joindre"}}, joindreFactures ? "oui" : "non", choisi -> {
                joindreFactures = "oui".equals(choisi);
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            p.contenu.addView(bloc, UiKit.pleineLargeur());
        } else if (Chantier.MODE_FORFAIT.equals(c.mode)) {
            p.contenu.addView(UiKit.text(a, "Chantier au forfait : aucune facture fournisseur n'est jointe, vos prix d'achat restent confidentiels.", 15, UiKit.ENCRE_2, false));
        }

        // PDF déjà générés.
        List<File> pdfs = store().fichiers(chantierId, "PDF client");
        if (!pdfs.isEmpty()) {
            p.contenu.addView(UiKit.section(a, "PDF déjà créés"));
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE);
            for (File pdf : pdfs) {
                LinearLayout ligne = carte();
                ligne.addView(UiKit.text(a, "Créé le " + f.format(new Date(pdf.lastModified())), 17, UiKit.ENCRE, true));
                LinearLayout boutons = UiKit.ligne(a, 8);
                boutons.addView(UiKit.boutonContour(a, "Voir", v -> Scanner.ouvrir(a, pdf)), UiKit.poids(1));
                boutons.addView(UiKit.boutonContour(a, "Envoyer", v -> envoyer(c, pdf)), UiKit.poids(1));
                ligne.addView(boutons, UiKit.pleineLargeur());
                p.contenu.addView(ligne, UiKit.pleineLargeur());
            }
        }
        p.contenu.addView(UiKit.text(a, "Ce récapitulatif accompagne votre facture et ne la remplace pas.", 15, UiKit.ENCRE_2, false));

        Button generer = UiKit.boutonAction(a, enCours ? "Création du PDF…" : "Créer le PDF", v -> creer(c, entreprise));
        generer.setEnabled(!enCours);
        p.ajouterAuPied(generer);
        return p.racine;
    }

    private void creer(Chantier c, Map<String, String> entreprise) {
        if (enCours) return;
        enCours = true;
        a.remplacer(this);
        RecapFin recap = RecapFin.construire(c, joindreFactures);
        new Thread(() -> {
            String erreur = null;
            File range = null;
            try {
                File tmp = PdfFinChantier.generer(a.getCacheDir(), store(), c, recap, entreprise);
                String chemin = store().importerFichier(chantierId, "PDF client", tmp, "fin-de-chantier-" + c.titre, "pdf");
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                range = store().fichier(chantierId, chemin);
            } catch (Exception e) {
                erreur = e.getMessage();
            }
            final String err = erreur;
            final File pdf = range;
            a.runOnUiThread(() -> {
                enCours = false;
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

    /** Partage le PDF (mail, WhatsApp, SMS…) avec un objet prêt. */
    private void envoyer(Chantier c, File pdf) {
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fichiers", pdf);
            Intent i = new Intent(Intent.ACTION_SEND)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, "Récapitulatif de fin de chantier – " + c.titre)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Envoyer le PDF au client"));
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
