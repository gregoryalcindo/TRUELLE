package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Ligne;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Postes;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.ui.PieceJointe;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un devis à lignes (quantité × prix, TVA par ligne, coût prévu), le nôtre ou celui d'un sous-traitant.
 * Brouillon → Envoyé → Signé (prix verrouillés). Pour changer un prix après signature : nouvelle version.
 */
public final class DevisScreen extends Screen {

    private final String chantierId;
    private final String devisId;

    private String sorte, libelle, prestataire, statut, remise;
    private boolean autoliquidation;
    /** « oui » quand le client a signé la mention de TVA réduite. */
    private String certification;
    /** Lignes en cours de modification (copie de travail). */
    List<Ligne> lignes;
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
        Finance fin = c == null ? null : a.finance(c);
        if (sorte == null && c != null) {
            sorte = edition ? existant.get("sorte") : Chantier.DEVIS_NOUS;
            libelle = edition ? existant.get("libelle") : "";
            prestataire = edition ? existant.get("prestataire") : "";
            statut = edition ? existant.get("statut") : Chantier.DEVIS_BROUILLON;
            remise = edition && existant.centimes("remise_bp") > 0 ? Tva.libelle((int) existant.centimes("remise_bp")).replace(" %", "") : "";
            autoliquidation = edition && Finance.autoliquidation(existant);
            lignes = new ArrayList<>();
            if (edition) for (Ligne l : fin.lignes(existant)) lignes.add(l.copie());
        }
        boolean nous = Chantier.DEVIS_NOUS.equals(sorte);
        boolean verrouille = edition && nous && Finance.signe(existant);
        boolean remplace = edition && Chantier.DEVIS_REMPLACE.equals(existant.get("statut"));

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        String titre = edition ? (nous ? "Devis" : "Devis sous-traitant") + (existant.get("version").isEmpty() ? "" : " v" + existant.get("version")) : "Nouveau devis";
        p.entete.addView(UiKit.titre(a, titre, 30));
        if (c == null) return p.racine;

        EditText fLibelle = UiKit.champTexte(a, libelle);
        fLibelle.setHint(nous ? "Ex. : Rénovation salle de bain" : "Ex. : Plomberie");
        EditText fPrestataire = UiKit.champ(a, prestataire, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fPrestataire.setHint("Nom du sous-traitant");
        EditText fRemise = UiKit.champ(a, remise, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fRemise.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        fRemise.setHint("0");
        Runnable garder = () -> {
            libelle = fLibelle.getText().toString().trim();
            prestataire = fPrestataire.getText().toString().trim();
            remise = fRemise.getText().toString().trim();
        };

        if (!edition) {
            p.contenu.addView(UiKit.choix(a, new String[][]{{Chantier.DEVIS_NOUS, "Notre devis"}, {Chantier.DEVIS_SOUS_TRAITANT, "Sous-traitant"}}, sorte, choisi -> {
                garder.run();
                sorte = choisi;
                statut = Chantier.DEVIS_NOUS.equals(choisi) ? Chantier.DEVIS_BROUILLON : Chantier.DEVIS_RECU;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        }
        if (verrouille) {
            p.contenu.addView(bandeau("Signé : les prix sont verrouillés. Pour les changer, créez une nouvelle version."), UiKit.pleineLargeur());
        } else if (remplace) {
            p.contenu.addView(bandeau("Remplacé par une version plus récente : ce devis ne compte plus."), UiKit.pleineLargeur());
        }

        // Le document : photo du devis signé, ou PDF reçu par mail.
        File actuel = nouveauFichier != null ? nouveauFichier : (edition ? store().fichier(chantierId, existant.get("fichier")) : null);
        p.contenu.addView(PieceJointe.bloc(a, actuel, "Document du devis", (fichier, ext, lu) -> {
            garder.run();
            nouveauFichier = fichier;
            nouvelleExtension = ext;
            if (lignes.isEmpty() && lu != null && lu.montantTrouve()) {
                Ligne l = new Ligne();
                l.designation = nous ? "Travaux" : "Sous-traitance";
                l.unite = "forfait";
                l.tvaBp = autoliquidation ? 0 : fin.tvaDefaut();
                l.puHtCentimes = Tva.ht(lu.montantCentimes, l.tvaBp);
                l.poste = nous ? Postes.MATERIAUX : Postes.SOUS_TRAITANCE;
                lignes.add(l);
            }
            if (!nous && prestataire.isEmpty() && lu != null && lu.fournisseur != null) prestataire = lu.fournisseur;
            a.remplacer(this);
        }), UiKit.pleineLargeur());

        p.contenu.addView(UiKit.libelle(a, "Intitulé", fLibelle), UiKit.pleineLargeur());
        if (!nous) {
            p.contenu.addView(UiKit.libelle(a, "Sous-traitant", fPrestataire), UiKit.pleineLargeur());
            LinearLayout bloc = UiKit.colonne(a, 8);
            bloc.addView(UiKit.text(a, "TVA du sous-traitant", 16, UiKit.ENCRE_2, false));
            bloc.addView(UiKit.choix(a, new String[][]{{"non", "Avec TVA"}, {"oui", "Autoliquidation"}}, autoliquidation ? "oui" : "non", choisi -> {
                garder.run();
                autoliquidation = "oui".equals(choisi);
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            bloc.addView(UiKit.text(a, "Autoliquidation : le sous-traitant facture sans TVA, c'est l'entreprise principale qui la déclare (CGI art. 283-2 nonies).", 15, UiKit.ENCRE_2, false));
            p.contenu.addView(bloc, UiKit.pleineLargeur());
        }

        // Lignes
        boolean sansTvaDevis = autoliquidation || fin.sansTvaFacturee() && nous;
        p.contenu.addView(UiKit.section(a, "Lignes du devis"));
        if (lignes.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucune ligne : ajoutez les ouvrages (quantité × prix).", 16, UiKit.ENCRE_2, false));
        for (int i = 0; i < lignes.size(); i++) {
            Ligne l = lignes.get(i);
            int index = i;
            boolean sansTva = sansTvaDevis;
            long ttcLigne = sansTva ? l.totalHt() : l.totalHt() + l.totalTva();
            String sous = Ligne.formatQuantite(l.quantiteMilli) + " " + l.unite + " × " + Money.format(sansTva ? l.puHtCentimes : Tva.ttc(l.puHtCentimes, l.tvaBp))
                    + (sansTva ? "" : " · TVA " + Tva.libelle(l.tvaBp))
                    + (l.coutPrevuCentimes > 0 ? " · coût prévu " + Money.format(l.coutPrevuCentimes) : "");
            p.contenu.addView(UiKit.ligneListe(a, l.designation.isEmpty() ? "Ligne " + (i + 1) : l.designation, sous, Money.format(ttcLigne), null,
                    verrouille || remplace ? null : v -> {
                        garder.run();
                        a.ouvrir(new LigneScreen(a, this, chantierId, index, !sansTvaDevis, nous));
                    }), UiKit.pleineLargeur());
        }
        if (!verrouille && !remplace) {
            p.contenu.addView(UiKit.boutonContour(a, "+ Ajouter une ligne", v -> {
                garder.run();
                a.ouvrir(new LigneScreen(a, this, chantierId, -1, !sansTvaDevis, nous));
            }), UiKit.pleineLargeur());
            if (nous) p.contenu.addView(UiKit.libelle(a, "Remise sur le devis (%)", fRemise), UiKit.pleineLargeur());
        }

        // Totaux
        int remiseBp = Math.max(0, Tva.parse(remise.isEmpty() ? "0" : remise));
        Finance.Totaux t = Finance.totaux(lignes, nous ? remiseBp : 0, sansTvaDevis);
        LinearLayout tot = UiKit.carte(a, 8);
        tot.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        tot.addView(UiKit.ligneChiffre(a, sansTvaDevis ? "Total" : "Total TTC", Money.format(t.ttc), true), UiKit.pleineLargeur());
        if (sansTvaDevis && nous && !fin.mentionTva().isEmpty()) tot.addView(UiKit.text(a, fin.mentionTva(), 14, UiKit.ENCRE_2, false));
        for (Map.Entry<Integer, Long> e : t.tvaParTaux.entrySet()) {
            if (e.getValue() > 0) tot.addView(UiKit.ligneChiffre(a, "dont TVA " + Tva.libelle(e.getKey()), Money.format(e.getValue()), false), UiKit.pleineLargeur());
        }
        if (t.tva > 0) tot.addView(UiKit.ligneChiffre(a, "Hors TVA", Money.format(t.ht), false), UiKit.pleineLargeur());
        if (t.remise > 0) tot.addView(UiKit.ligneChiffre(a, "Remise accordée", "− " + Money.format(t.remise), false), UiKit.pleineLargeur());
        if (nous && t.coutPrevu > 0) {
            long fg = fin.fraisGeneraux(t.coutPrevu);
            long benef = t.ht - t.coutPrevu - fg;
            tot.addView(UiKit.ligneChiffre(a, "Coût prévu", Money.format(t.coutPrevu), false), UiKit.pleineLargeur());
            if (fg > 0) tot.addView(UiKit.ligneChiffre(a, "Frais généraux (" + Tva.libelle(fin.r.fraisGenerauxBp) + ")", Money.format(fg), false), UiKit.pleineLargeur());
            TextView b = UiKit.text(a, "Bénéfice prévu : " + Money.format(benef) + (t.ht > 0 ? " (" + Finance.pourcent(Tva.arrondi(benef * 10000L, t.ht)) + ")" : ""), 17, benef < 0 ? UiKit.ROUGE : UiKit.ENCRE, true);
            tot.addView(b);
        }
        p.contenu.addView(tot, UiKit.pleineLargeur());

        // TVA réduite (5,5 % ou 10 %) : le client doit certifier sur le devis, sinon c'est 20 % sur tout.
        boolean a55 = false, a10 = false;
        for (Map.Entry<Integer, Long> e : t.baseParTaux.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (e.getKey() == Tva.REDUIT) a55 = true;
            if (e.getKey() == Tva.INTERMEDIAIRE) a10 = true;
        }
        if (nous && !sansTvaDevis && (a55 || a10)) {
            if (certification == null) certification = edition ? existant.get("certification") : "";
            boolean petit = t.ttc < com.gregory.truelle.data.TvaGuide.SEUIL_SANS_CERTIFICATION_TTC;
            boolean ok = "oui".equals(certification);
            LinearLayout cert = UiKit.carte(a, 10);
            cert.setBackground(UiKit.rounded(ok || petit ? UiKit.SURFACE : UiKit.AMBRE_FOND, dp(16), 0, 0));
            cert.addView(UiKit.titre(a, "TVA à " + (a55 && a10 ? "5,5 % et 10 %" : a55 ? "5,5 %" : "10 %") + " : mention du client", 19));
            cert.addView(UiKit.text(a, petit
                    ? "Moins de 1 000 € TTC d'entretien ou de réparation : la mention n'est pas exigée. Le devis doit indiquer le nom et l'adresse du client et du logement, la nature des travaux et « logement achevé depuis plus de 2 ans »."
                    : "Le client doit recopier et signer cette mention sur le devis (ou la facture). Sans elle, le taux normal de 20 % s'applique à tout le chantier.",
                    15, petit || ok ? UiKit.ENCRE_2 : UiKit.AMBRE, !petit && !ok));
            String mention = (a10 ? com.gregory.truelle.data.TvaGuide.MENTION_DEVIS_10 : "") + (a10 && a55 ? "\n\n" : "")
                    + (a55 ? com.gregory.truelle.data.TvaGuide.MENTION_DEVIS_55 : "");
            TextView mt = UiKit.text(a, mention, 14, UiKit.ENCRE, false);
            mt.setPadding(dp(12), dp(10), dp(12), dp(10));
            mt.setBackground(UiKit.rounded(UiKit.FOND, dp(10), 0, 0));
            mt.setTextIsSelectable(true);
            cert.addView(mt, UiKit.pleineLargeur());
            cert.addView(UiKit.boutonContour(a, "Copier la mention", v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Mention TVA", mention));
                toast("Mention copiée");
            }), UiKit.pleineLargeur());
            if (!petit) {
                cert.addView(UiKit.text(a, "Le client l'a signée ?", 16, UiKit.ENCRE_2, false));
                cert.addView(UiKit.choix(a, new String[][]{{"", "Pas encore"}, {"oui", "Oui, signée"}}, ok ? "oui" : "", choisi -> {
                    garder.run();
                    certification = choisi;
                    if (edition) {
                        Map<String, String> d = new java.util.LinkedHashMap<>();
                        d.put("cible", existant.id);
                        d.put("certification", choisi);
                        d.put("certification_le", "oui".equals(choisi) ? com.gregory.truelle.data.Dates.aujourdhui() : "");
                        if (enregistrer(chantierId, com.gregory.truelle.data.Event.CORRECTION, d) && "oui".equals(choisi)) toast("Mention signée ✓");
                    }
                    a.remplacer(this);
                }), UiKit.pleineLargeur());
            }
            p.contenu.addView(cert, UiKit.pleineLargeur());
        }

        // Statut
        if (!remplace) {
            LinearLayout blocStatut = UiKit.colonne(a, 8);
            blocStatut.addView(UiKit.text(a, "Statut", 16, UiKit.ENCRE_2, false));
            String[][] options = nous
                    ? new String[][]{{Chantier.DEVIS_BROUILLON, "Brouillon"}, {Chantier.DEVIS_ENVOYE, "Envoyé"}, {Chantier.DEVIS_SIGNE, "Signé"}, {Chantier.DEVIS_REFUSE, "Refusé"}}
                    : new String[][]{{Chantier.DEVIS_RECU, "Reçu"}, {Chantier.DEVIS_SIGNE, "Accepté"}, {Chantier.DEVIS_REFUSE, "Refusé"}};
            blocStatut.addView(UiKit.choix(a, options, statut, choisi -> {
                garder.run();
                statut = choisi;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            blocStatut.addView(UiKit.text(a, nous
                    ? "Un devis signé compte dans le montant du chantier, et ses coûts prévus deviennent le budget."
                    : "Un devis accepté est un coût engagé du chantier.", 15, UiKit.ENCRE_2, false));
            p.contenu.addView(blocStatut, UiKit.pleineLargeur());
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            if (lignes.isEmpty()) {
                toast("Ajoutez au moins une ligne");
                return;
            }
            int rb = remise.isEmpty() ? 0 : Tva.parse(remise);
            if (rb < 0) {
                fRemise.setError("Entre 0 et 100");
                return;
            }
            Finance.Totaux tt = Finance.totaux(lignes, nous ? rb : 0, sansTvaDevis);
            String fichier = null;
            if (nouveauFichier != null) {
                try {
                    fichier = store().importerFichier(chantierId, "Devis", nouveauFichier,
                            com.gregory.truelle.data.NomsFichiers.devis(nous, libelle, prestataire, tt.ttc), nouvelleExtension);
                } catch (Exception e) {
                    toast("Le document n'a pas pu être rangé : " + e.getMessage());
                    return;
                }
            }
            Map<String, String> data = new LinkedHashMap<>();
            boolean ok;
            String lignesTxt = Ligne.encoder(lignes);
            if (!edition) {
                data.put("sorte", sorte);
                data.put("libelle", libelle);
                if (!nous) data.put("prestataire", prestataire);
                data.put("lignes", lignesTxt);
                data.put("remise_bp", Integer.toString(nous ? rb : 0));
                data.put("autoliquidation", autoliquidation ? "oui" : "non");
                if ("oui".equals(certification)) {
                    data.put("certification", "oui");
                    data.put("certification_le", com.gregory.truelle.data.Dates.aujourdhui());
                }
                data.put("montant_centimes", Long.toString(tt.ttc));
                data.put("statut", statut);
                data.put("version", "1");
                if (fichier != null) data.put("fichier", fichier);
                ok = enregistrer(chantierId, Event.DEVIS, data);
            } else {
                data.put("cible", existant.id);
                if (!libelle.equals(existant.get("libelle"))) data.put("libelle", libelle);
                if (!nous && !prestataire.equals(existant.get("prestataire"))) data.put("prestataire", prestataire);
                if (!verrouille && !lignesTxt.equals(existant.get("lignes"))) data.put("lignes", lignesTxt);
                if (!verrouille && rb != existant.centimes("remise_bp")) data.put("remise_bp", Integer.toString(rb));
                if (autoliquidation != Finance.autoliquidation(existant)) data.put("autoliquidation", autoliquidation ? "oui" : "non");
                if (!verrouille && tt.ttc != existant.centimes("montant_centimes")) data.put("montant_centimes", Long.toString(tt.ttc));
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

        if (verrouille) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Nouvelle version (modifier les prix)", v -> nouvelleVersion(existant)));
        }
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

    /** Copie modifiable du devis signé ; l'ancien passe en « Remplacé ». */
    private void nouvelleVersion(Chantier.Element ancien) {
        new AlertDialog.Builder(a)
                .setTitle("Créer une nouvelle version ?")
                .setMessage("Une copie en brouillon sera créée avec les mêmes lignes. L'ancien devis restera consultable, marqué « Remplacé ». Pensez à faire signer la nouvelle version.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Créer", (d, w) -> {
                    int version = (int) Math.max(1, ancien.centimes("version")) + 1;
                    Map<String, String> data = new LinkedHashMap<>();
                    data.put("sorte", ancien.get("sorte"));
                    data.put("libelle", ancien.get("libelle"));
                    data.put("lignes", Ligne.encoder(lignes));
                    data.put("remise_bp", ancien.get("remise_bp").isEmpty() ? "0" : ancien.get("remise_bp"));
                    data.put("autoliquidation", ancien.get("autoliquidation").isEmpty() ? "non" : ancien.get("autoliquidation"));
                    data.put("montant_centimes", ancien.get("montant_centimes"));
                    data.put("statut", Chantier.DEVIS_BROUILLON);
                    data.put("version", Integer.toString(version));
                    data.put("remplace", ancien.id);
                    Event e = Event.create(Event.DEVIS, chantierId, a.auteur(), data);
                    try {
                        store().ajouter(chantierId, e);
                        Map<String, String> corr = new LinkedHashMap<>();
                        corr.put("cible", ancien.id);
                        corr.put("statut", Chantier.DEVIS_REMPLACE);
                        corr.put("remplace_par", e.id);
                        store().ajouter(chantierId, Event.create(Event.CORRECTION, chantierId, a.auteur(), corr));
                        a.apresEnregistrement();
                        toast("Version " + version + " créée");
                        a.remplacer(new DevisScreen(a, chantierId, e.id));
                    } catch (Exception ex) {
                        toast("Création impossible : " + ex.getMessage());
                    }
                }).show();
    }

    private View bandeau(String texte) {
        TextView t = UiKit.text(a, texte, 16, UiKit.BLEU, true);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(UiKit.rounded(UiKit.BLEU_FOND, dp(12), 0, 0));
        return t;
    }
}
