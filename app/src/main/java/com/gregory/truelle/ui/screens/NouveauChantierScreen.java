package com.gregory.truelle.ui.screens;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nouveau chantier : la base suffit (travaux, client, ce que le client paie, ce que ça devrait coûter).
 * Le tiroir « Plus de précisions » ajoute l'adresse, le téléphone et le type de chantier.
 */
public final class NouveauChantierScreen extends Screen {

    private String titre = "", client = "", prix = "", cout = "", adresse = "", telephone = "";
    private String type = Chantier.TYPE_PRO;
    private boolean precisions;
    private EditText fTitre, fClient, fPrix, fCout, fAdresse, fTel;

    public NouveauChantierScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Nouveau chantier", 32));
        boolean proches = Chantier.TYPE_PROCHES.equals(type);
        boolean sansTva = proches || a.reglages().franchise();

        fTitre = UiKit.champTexte(a, titre);
        fTitre.setHint("Ex. : Salle de bain");
        fClient = UiKit.champ(a, client, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fClient.setHint("Nom du client");
        fPrix = UiKit.champMontant(a, prix);
        fPrix.setHint("Facultatif");
        fCout = UiKit.champMontant(a, cout);
        fCout.setHint("Facultatif");

        p.contenu.addView(UiKit.libelle(a, "Travaux", fTitre), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Client", fClient), UiKit.pleineLargeur());
        LinearLayout duo = UiKit.ligne(a, 10);
        duo.setGravity(android.view.Gravity.TOP);
        duo.addView(UiKit.libelle(a, sansTva ? "Le client paie" : "Le client paie (TTC)", UiKit.euro(a, fPrix)), UiKit.poids(1));
        duo.addView(UiKit.libelle(a, "Ça va me coûter environ", UiKit.euro(a, fCout)), UiKit.poids(1));
        p.contenu.addView(duo, UiKit.pleineLargeur());
        p.contenu.addView(UiKit.text(a, sansTva ? "Vous pourrez tout préciser plus tard depuis la fiche du chantier."
                : "TVA : " + com.gregory.truelle.data.Tva.libelle(a.reglages().tvaDefautBp) + " par défaut, à changer depuis la fiche (le guide vous aide). "
                + "Tout se précise plus tard.", 15, UiKit.ENCRE_2, false));

        // Tiroir : plus de précisions
        if (!precisions) {
            p.contenu.addView(UiKit.boutonContour(a, "Plus de précisions (adresse, téléphone, type) ›", v -> {
                garder();
                precisions = true;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        } else {
            fAdresse = UiKit.champ(a, adresse, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            fAdresse.setHint("Adresse du chantier");
            fTel = UiKit.champ(a, telephone, InputType.TYPE_CLASS_PHONE);
            fTel.setHint("Pour l'appeler depuis la fiche");
            p.contenu.addView(UiKit.libelle(a, "Adresse", fAdresse), UiKit.pleineLargeur());
            p.contenu.addView(UiKit.libelle(a, "Téléphone du client", fTel), UiKit.pleineLargeur());
            LinearLayout blocType = UiKit.colonne(a, 8);
            blocType.addView(UiKit.text(a, "Type de chantier", 16, UiKit.ENCRE_2, false));
            blocType.addView(UiKit.choix(a, new String[][]{{Chantier.TYPE_PRO, "Professionnel"}, {Chantier.TYPE_PROCHES, "Entre proches"}}, type, choisi -> {
                garder();
                type = choisi;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            blocType.addView(UiKit.text(a, proches ? "Même suivi qu'un chantier pro, mais sans aucune TVA." : "Chantier déclaré, avec TVA.", 15, UiKit.ENCRE_2, false));
            p.contenu.addView(blocType, UiKit.pleineLargeur());
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Créer le chantier", v -> {
            garder();
            if (titre.isEmpty()) {
                fTitre.setError("Indiquez les travaux");
                fTitre.requestFocus();
                return;
            }
            long prixC = prix.isEmpty() ? 0 : Money.parseCentimes(prix);
            long coutC = cout.isEmpty() ? 0 : Money.parseCentimes(cout);
            if (prixC < 0) {
                fPrix.setError("Montant invalide");
                return;
            }
            if (coutC < 0) {
                fCout.setError("Montant invalide");
                return;
            }
            Map<String, String> extra = new LinkedHashMap<>();
            extra.put("type", type);
            if (proches) extra.put("tva_defaut_bp", "0");
            if (!telephone.isEmpty()) extra.put("telephone", telephone);
            if (prixC > 0) extra.put("prix_centimes", Long.toString(prixC));
            if (coutC > 0) extra.put("cout_prevu_centimes", Long.toString(coutC));
            try {
                // Les dépenses refacturées se choisissent en fin de chantier, par catégorie.
                Chantier c = store().creerChantier(titre, client, adresse, Chantier.MODE_FORFAIT, a.auteur(), extra);
                a.apresEnregistrement();
                toast("Chantier créé ✓");
                a.remplacer(new FicheScreen(a, c.id));
            } catch (Exception e) {
                toast("Création impossible : " + e.getMessage());
            }
        }));
        if (titre.isEmpty()) fTitre.requestFocus();
        return p.racine;
    }

    private void garder() {
        titre = fTitre.getText().toString().trim();
        client = fClient.getText().toString().trim();
        prix = fPrix.getText().toString().trim();
        cout = fCout.getText().toString().trim();
        if (fAdresse != null && precisions) adresse = fAdresse.getText().toString().trim();
        if (fTel != null && precisions) telephone = fTel.getText().toString().trim();
    }
}
