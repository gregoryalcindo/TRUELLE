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
import com.gregory.truelle.ui.PieceJointe;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Un devis : le nôtre (envoyé, signé, refusé) ou celui d'un sous-traitant (reçu, accepté, refusé). */
public final class DevisScreen extends Screen {

    private final String chantierId;
    private final String devisId;

    private String sorte, libelle, prestataire, montant, statut;
    private File nouveauFichier;
    private String nouvelleExtension;

    public DevisScreen(MainActivity a, String chantierId, String devisId) {
        super(a);
        this.chantierId = chantierId;
        this.devisId = devisId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Element existant = (c != null && devisId != null) ? c.element(devisId) : null;
        boolean edition = existant != null;
        if (sorte == null) {
            sorte = edition ? existant.get("sorte") : Chantier.DEVIS_NOUS;
            libelle = edition ? existant.get("libelle") : "";
            prestataire = edition ? existant.get("prestataire") : "";
            montant = edition ? Money.toInput(existant.centimes("montant_centimes")) : "";
            statut = edition ? existant.get("statut") : Chantier.DEVIS_ENVOYE;
        }
        boolean nous = Chantier.DEVIS_NOUS.equals(sorte);

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, edition ? "Modifier le devis" : "Nouveau devis", 30));
        if (c == null) return p.racine;

        EditText fLibelle = UiKit.champTexte(a, libelle);
        fLibelle.setHint(nous ? "Ex. : Devis initial" : "Ex. : Plomberie");
        EditText fPrestataire = UiKit.champ(a, prestataire, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fPrestataire.setHint("Nom du sous-traitant");
        EditText fMontant = UiKit.champMontant(a, montant);
        Runnable garder = () -> {
            libelle = fLibelle.getText().toString().trim();
            prestataire = fPrestataire.getText().toString().trim();
            montant = fMontant.getText().toString().trim();
        };

        if (!edition) {
            p.contenu.addView(UiKit.choix(a, new String[][]{{Chantier.DEVIS_NOUS, "Notre devis"}, {Chantier.DEVIS_SOUS_TRAITANT, "Sous-traitant"}}, sorte, choisi -> {
                garder.run();
                sorte = choisi;
                statut = Chantier.DEVIS_NOUS.equals(choisi) ? Chantier.DEVIS_ENVOYE : Chantier.DEVIS_RECU;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        } else {
            p.contenu.addView(UiKit.text(a, nous ? "Notre devis" : "Devis de sous-traitant", 17, UiKit.ENCRE_2, true));
        }

        // Le document : photo du devis signé, ou PDF reçu par mail.
        File actuel = nouveauFichier != null ? nouveauFichier : (edition ? store().fichier(chantierId, existant.get("fichier")) : null);
        p.contenu.addView(PieceJointe.bloc(a, actuel, "Document du devis", (fichier, ext, lu) -> {
            garder.run();
            nouveauFichier = fichier;
            nouvelleExtension = ext;
            if (montant.isEmpty() && lu != null && lu.montantTrouve()) montant = Money.toInput(lu.montantCentimes);
            if (!nous && prestataire.isEmpty() && lu != null && lu.fournisseur != null) prestataire = lu.fournisseur;
            a.remplacer(this);
        }), UiKit.pleineLargeur());

        p.contenu.addView(UiKit.libelle(a, "Intitulé", fLibelle), UiKit.pleineLargeur());
        if (!nous) p.contenu.addView(UiKit.libelle(a, "Sous-traitant", fPrestataire), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Montant TTC", UiKit.euro(a, fMontant)), UiKit.pleineLargeur());

        LinearLayout blocStatut = UiKit.colonne(a, 8);
        blocStatut.addView(UiKit.text(a, "Statut", 16, UiKit.ENCRE_2, false));
        String[][] options = nous
                ? new String[][]{{Chantier.DEVIS_ENVOYE, "Envoyé"}, {Chantier.DEVIS_SIGNE, "Signé"}, {Chantier.DEVIS_REFUSE, "Refusé"}}
                : new String[][]{{Chantier.DEVIS_RECU, "Reçu"}, {Chantier.DEVIS_SIGNE, "Accepté"}, {Chantier.DEVIS_REFUSE, "Refusé"}};
        blocStatut.addView(UiKit.choix(a, options, statut, choisi -> {
            garder.run();
            statut = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        blocStatut.addView(UiKit.text(a, nous
                ? "Un devis signé compte dans le montant du chantier."
                : "Un devis accepté compte dans les coûts du chantier.", 15, UiKit.ENCRE_2, false));
        p.contenu.addView(blocStatut, UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            long centimes = Money.parseCentimes(montant);
            if (centimes <= 0) {
                fMontant.setError("Indiquez le montant du devis");
                return;
            }
            String fichier = null;
            if (nouveauFichier != null) {
                try {
                    fichier = store().importerFichier(chantierId, "Devis", nouveauFichier,
                            com.gregory.truelle.data.NomsFichiers.devis(nous, libelle, prestataire, centimes), nouvelleExtension);
                } catch (Exception e) {
                    toast("Le document n'a pas pu être rangé : " + e.getMessage());
                    return;
                }
            }
            Map<String, String> data = new LinkedHashMap<>();
            boolean ok;
            if (!edition) {
                data.put("sorte", sorte);
                data.put("libelle", libelle);
                if (!nous) data.put("prestataire", prestataire);
                data.put("montant_centimes", Long.toString(centimes));
                data.put("statut", statut);
                if (fichier != null) data.put("fichier", fichier);
                ok = enregistrer(chantierId, Event.DEVIS, data);
            } else {
                data.put("cible", existant.id);
                if (!libelle.equals(existant.get("libelle"))) data.put("libelle", libelle);
                if (!nous && !prestataire.equals(existant.get("prestataire"))) data.put("prestataire", prestataire);
                if (centimes != existant.centimes("montant_centimes")) data.put("montant_centimes", Long.toString(centimes));
                if (!statut.equals(existant.get("statut"))) data.put("statut", statut);
                if (fichier != null) data.put("fichier", fichier);
                ok = data.size() == 1 || enregistrer(chantierId, Event.CORRECTION, data);
            }
            if (ok) {
                if (nouveauFichier != null) {
                    //noinspection ResultOfMethodCallIgnored
                    nouveauFichier.delete();
                }
                toast("Devis enregistré ✓");
                a.retour();
            }
        }));

        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer ce devis", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer ce devis ?")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existant.id))) {
                            toast("Devis supprimé");
                            a.retour();
                        }
                    })
                    .show()));
        }
        return p.racine;
    }
}
