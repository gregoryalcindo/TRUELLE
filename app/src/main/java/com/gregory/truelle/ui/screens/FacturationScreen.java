package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Une ligne du suivi de facturation interne (acompte, situation, solde…). Pas une facture légale. */
public final class FacturationScreen extends Screen {

    private final String chantierId;
    private final String ligneId;
    private String libelle, montant, statut, numero;

    public FacturationScreen(MainActivity a, String chantierId, String ligneId) {
        super(a);
        this.chantierId = chantierId;
        this.ligneId = ligneId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Element existant = (c != null && ligneId != null) ? c.element(ligneId) : null;
        boolean edition = existant != null;
        if (libelle == null) {
            libelle = edition ? existant.get("libelle") : "";
            montant = edition ? Money.toInput(existant.centimes("montant_centimes")) : "";
            statut = edition ? existant.get("statut") : Chantier.FACT_A_FACTURER;
            numero = edition ? existant.get("numero") : "";
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, edition ? "Ligne de facturation" : "Nouvelle ligne", 30));
        if (c == null) return p.racine;

        EditText fLibelle = UiKit.champTexte(a, libelle);
        fLibelle.setHint("Ex. : Acompte 30 %, Situation n°1, Solde");
        EditText fMontant = UiKit.champMontant(a, montant);
        EditText fNumero = UiKit.champ(a, numero, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        fNumero.setHint("Facultatif");
        Runnable garder = () -> {
            libelle = fLibelle.getText().toString().trim();
            montant = fMontant.getText().toString().trim();
            numero = fNumero.getText().toString().trim();
        };

        if (!edition) {
            // Raccourcis pour les cas courants.
            LinearLayout raccourcis = UiKit.ligne(a, 8);
            for (String r : new String[]{"Acompte", "Situation", "Solde"}) {
                raccourcis.addView(UiKit.boutonChoix(a, r, r.equals(libelle), UiKit.ENCRE, android.graphics.Color.WHITE, v -> {
                    garder.run();
                    libelle = r;
                    a.remplacer(this);
                }), UiKit.poids(1));
            }
            p.contenu.addView(raccourcis, UiKit.pleineLargeur());
        }

        p.contenu.addView(UiKit.libelle(a, "Intitulé", fLibelle), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Montant TTC", UiKit.euro(a, fMontant)), UiKit.pleineLargeur());

        LinearLayout blocStatut = UiKit.colonne(a, 8);
        blocStatut.addView(UiKit.text(a, "Statut", 16, UiKit.ENCRE_2, false));
        blocStatut.addView(UiKit.choix(a, new String[][]{{Chantier.FACT_A_FACTURER, "À facturer"}, {Chantier.FACT_FACTURE, "Facturé"}, {Chantier.FACT_ENCAISSE, "Encaissé"}}, statut, choisi -> {
            garder.run();
            statut = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        p.contenu.addView(blocStatut, UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "N° de la vraie facture", fNumero), UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            if (libelle.isEmpty()) {
                fLibelle.setError("Indiquez l'intitulé");
                return;
            }
            long centimes = Money.parseCentimes(montant);
            if (centimes <= 0) {
                fMontant.setError("Indiquez le montant");
                return;
            }
            Map<String, String> data = new LinkedHashMap<>();
            boolean ok;
            if (!edition) {
                data.put("libelle", libelle);
                data.put("montant_centimes", Long.toString(centimes));
                data.put("statut", statut);
                if (!numero.isEmpty()) data.put("numero", numero);
                ok = enregistrer(chantierId, Event.FACTURATION, data);
            } else {
                data.put("cible", existant.id);
                if (!libelle.equals(existant.get("libelle"))) data.put("libelle", libelle);
                if (centimes != existant.centimes("montant_centimes")) data.put("montant_centimes", Long.toString(centimes));
                if (!statut.equals(existant.get("statut"))) data.put("statut", statut);
                if (!numero.equals(existant.get("numero"))) data.put("numero", numero);
                ok = data.size() == 1 || enregistrer(chantierId, Event.CORRECTION, data);
            }
            if (ok) {
                toast("Ligne enregistrée ✓");
                a.retour();
            }
        }));
        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer cette ligne", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer cette ligne ?")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existant.id))) {
                            toast("Ligne supprimée");
                            a.retour();
                        }
                    })
                    .show()));
        }
        return p.racine;
    }
}
