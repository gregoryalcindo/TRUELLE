package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Écran 8 des maquettes : un travail en plus (Proposé → Accepté → Fait), avec la signature du client. */
public final class TravauxScreen extends Screen {

    private final String chantierId;
    private final String travauxId;
    private String description, montant, statut;

    public TravauxScreen(MainActivity a, String chantierId, String travauxId) {
        super(a);
        this.chantierId = chantierId;
        this.travauxId = travauxId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Element existant = (c != null && travauxId != null) ? c.element(travauxId) : null;
        boolean edition = existant != null;
        if (description == null) {
            description = edition ? existant.get("description") : "";
            montant = edition ? Money.toInput(existant.centimes("montant_centimes")) : "";
            statut = edition ? existant.get("statut") : Chantier.TRAVAUX_PROPOSE;
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, edition ? "Travaux en plus" : "Nouveaux travaux", 30));
        if (c == null) return p.racine;

        EditText fDesc = UiKit.champ(a, description, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        fDesc.setMinLines(2);
        fDesc.setGravity(Gravity.TOP | Gravity.START);
        fDesc.setHint("Ex. : faïence jusqu'au plafond");
        EditText fMontant = UiKit.champMontant(a, montant);
        Runnable garder = () -> {
            description = fDesc.getText().toString().trim();
            montant = fMontant.getText().toString().trim();
        };

        p.contenu.addView(UiKit.libelle(a, "Description", fDesc), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Montant TTC", UiKit.euro(a, fMontant)), UiKit.pleineLargeur());

        LinearLayout blocStatut = UiKit.colonne(a, 8);
        blocStatut.addView(UiKit.text(a, "Statut", 16, UiKit.ENCRE_2, false));
        blocStatut.addView(UiKit.choix(a, new String[][]{{Chantier.TRAVAUX_PROPOSE, "Proposé"}, {Chantier.TRAVAUX_ACCEPTE, "Accepté"}, {Chantier.TRAVAUX_FAIT, "Fait"}}, statut, choisi -> {
            garder.run();
            statut = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        p.contenu.addView(blocStatut, UiKit.pleineLargeur());

        // Signature : la preuve d'accord du client.
        File signature = edition ? store().fichier(chantierId, existant.get("signature")) : null;
        if (signature != null) {
            LinearLayout carte = UiKit.carte(a, 10);
            carte.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
            carte.addView(UiKit.text(a, "✓ Signé par le client le " + existant.get("signe_le"), 18, UiKit.VERT, true));
            ImageView iv = new ImageView(a);
            Bitmap bmp = Scanner.apercuFichier(signature, dp(300));
            if (bmp != null) iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            iv.setContentDescription("Signature du client");
            carte.addView(iv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140)));
            p.contenu.addView(carte, UiKit.pleineLargeur());
        } else {
            p.contenu.addView(UiKit.text(a, "Au forfait, tout travail en plus doit être accepté par écrit, avec son prix. Faites signer le client sur le téléphone.", 15, UiKit.ENCRE_2, false));
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            String id = sauver(existant, fDesc, fMontant);
            if (id != null) {
                toast("Travaux enregistrés ✓");
                a.retour();
            }
        }));
        if (signature == null) {
            p.ajouterAuPied(UiKit.boutonContour(a, edition ? "Faire signer le client" : "Enregistrer et faire signer", v -> {
                garder.run();
                String id = sauver(existant, fDesc, fMontant);
                if (id != null) a.remplacer(new SignatureScreen(a, chantierId, id));
            }));
        }
        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer ces travaux ?")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existant.id))) {
                            toast("Travaux supprimés");
                            a.retour();
                        }
                    })
                    .show()));
        }
        return p.racine;
    }

    /** Enregistre (création ou correction) ; retourne l'identifiant des travaux, ou null si erreur. */
    private String sauver(Chantier.Element existant, EditText fDesc, EditText fMontant) {
        if (description.isEmpty()) {
            fDesc.setError("Décrivez les travaux");
            return null;
        }
        long centimes = Money.parseCentimes(montant);
        if (centimes <= 0) {
            fMontant.setError("Indiquez le montant");
            return null;
        }
        if (existant == null) {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("description", description);
            data.put("montant_centimes", Long.toString(centimes));
            data.put("statut", statut);
            Event e = Event.create(Event.TRAVAUX, chantierId, a.auteur(), data);
            try {
                store().ajouter(chantierId, e);
                a.apresEnregistrement();
                return e.id;
            } catch (Exception ex) {
                toast("Enregistrement impossible : " + ex.getMessage());
                return null;
            }
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("cible", existant.id);
        if (!description.equals(existant.get("description"))) data.put("description", description);
        if (centimes != existant.centimes("montant_centimes")) data.put("montant_centimes", Long.toString(centimes));
        if (!statut.equals(existant.get("statut"))) data.put("statut", statut);
        if (data.size() == 1 || enregistrer(chantierId, Event.CORRECTION, data)) return existant.id;
        return null;
    }
}
