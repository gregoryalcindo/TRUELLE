package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

/** Création d'un chantier : travaux, client, adresse, et mode de facturation du matériel. */
public final class NouveauChantierScreen extends Screen {

    private String mode = Chantier.MODE_FORFAIT;
    private String titre = "";
    private String client = "";
    private String adresse = "";
    private String type = Chantier.TYPE_PRO;
    private String taux = "";

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

        EditText fTitre = UiKit.champTexte(a, titre);
        fTitre.setHint("Ex. : Salle de bain");
        EditText fClient = UiKit.champ(a, client, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fClient.setHint("Nom du client");
        EditText fAdresse = UiKit.champ(a, adresse, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fAdresse.setHint("Adresse du chantier");

        EditText fTaux = UiKit.champMontant(a, taux);
        fTaux.setHint("Ex. : 20");
        boolean proches = Chantier.TYPE_PROCHES.equals(type);

        // Type de chantier : décide de tout le reste (TVA, devis, bénéfice… ou simple décompte).
        LinearLayout blocType = UiKit.colonne(a, 8);
        blocType.addView(UiKit.text(a, "Type de chantier", 16, UiKit.ENCRE_2, false));
        blocType.addView(UiKit.choix(a, new String[][]{{Chantier.TYPE_PRO, "Professionnel"}, {Chantier.TYPE_PROCHES, "Entre proches"}}, type, choisi -> {
            garder(fTitre, fClient, fAdresse, fTaux);
            type = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        blocType.addView(UiKit.text(a, proches
                ? "Pour la famille ou un ami : les achats sont remboursés au prix payé, la main d'œuvre est payée au taux convenu. Pas de TVA, pas de bénéfice."
                : "Pour un client : devis, TVA, budget, bénéfice et facturation.", 15, UiKit.ENCRE_2, false));
        p.contenu.addView(blocType, UiKit.pleineLargeur());

        p.contenu.addView(UiKit.libelle(a, "Travaux", fTitre), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, proches ? "Pour qui" : "Client", fClient), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Adresse", fAdresse), UiKit.pleineLargeur());
        if (proches) p.contenu.addView(UiKit.libelle(a, "Taux horaire convenu (€/h, facultatif)", UiKit.euro(a, fTaux)), UiKit.pleineLargeur());

        // Mode du matériel : la sélection est en noir, comme partout dans l'appli.
        LinearLayout blocMode = UiKit.colonne(a, 8);
        blocMode.addView(UiKit.text(a, "Matériel", 16, UiKit.ENCRE_2, false));
        LinearLayout choix = UiKit.ligne(a, 8);
        View.OnClickListener reel = v -> {
            garder(fTitre, fClient, fAdresse, fTaux);
            mode = Chantier.MODE_REEL;
            a.remplacer(this);
        };
        View.OnClickListener forfait = v -> {
            garder(fTitre, fClient, fAdresse, fTaux);
            mode = Chantier.MODE_FORFAIT;
            a.remplacer(this);
        };
        choix.addView(UiKit.boutonChoix(a, "Refacturé au réel", Chantier.MODE_REEL.equals(mode), UiKit.ENCRE, Color.WHITE, reel), UiKit.poids(1));
        choix.addView(UiKit.boutonChoix(a, "Inclus au forfait", Chantier.MODE_FORFAIT.equals(mode), UiKit.ENCRE, Color.WHITE, forfait), UiKit.poids(1));
        blocMode.addView(choix, UiKit.pleineLargeur());
        blocMode.addView(UiKit.text(a, Chantier.MODE_REEL.equals(mode)
                ? "Les factures fournisseurs pourront être jointes au PDF client."
                : "Les dépenses restent internes : elles servent à la rentabilité.", 15, UiKit.ENCRE_2, false));
        if (!proches) p.contenu.addView(blocMode, UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Créer le chantier", v -> {
            garder(fTitre, fClient, fAdresse, fTaux);
            if (titre.isEmpty()) {
                fTitre.setError("Indiquez les travaux");
                fTitre.requestFocus();
                return;
            }
            long t = taux.isEmpty() ? 0 : com.gregory.truelle.data.Money.parseCentimes(taux);
            if (t < 0) {
                fTaux.setError("Taux invalide");
                return;
            }
            java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();
            extra.put("type", type);
            if (proches) {
                extra.put("taux_convenu_centimes", Long.toString(t));
                extra.put("tva_defaut_bp", "0");
            }
            try {
                Chantier c = store().creerChantier(titre, client, adresse, proches ? Chantier.MODE_REEL : mode, a.auteur(), extra);
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

    private void garder(EditText t, EditText c, EditText ad, EditText tx) {
        taux = tx.getText().toString().trim();
        titre = t.getText().toString().trim();
        client = c.getText().toString().trim();
        adresse = ad.getText().toString().trim();
    }
}
