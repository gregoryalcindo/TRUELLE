package com.gregory.truelle.ui.screens;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Dates;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Ligne;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.data.TvaGuide;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caisse « Prix » : ce que le client paie, en un montant.
 * Tiroir suivant : « Détailler en lignes », qui transforme ce prix en devis.
 */
public final class PrixScreen extends Screen {

    private final String chantierId;
    private String montant;
    private int tvaBp = -2;
    private String certification;

    public PrixScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Retour", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Prix du chantier", 30));
        if (c == null) return p.racine;
        Finance f = a.finance(c);
        boolean sansTva = f.sansTvaFacturee();
        if (montant == null) {
            montant = c.prixCentimes > 0 ? Money.toInput(c.prixCentimes) : "";
            tvaBp = f.tvaPrix();
            certification = c.certification;
        }

        EditText fMontant = UiKit.champMontant(a, montant);
        fMontant.setHint("Ex. : 1 000");
        Runnable garder = () -> montant = fMontant.getText().toString().trim();
        p.contenu.addView(UiKit.libelle(a, sansTva ? "Le client paie" : "Le client paie (TTC)", UiKit.euro(a, fMontant)), UiKit.pleineLargeur());
        if (!f.mentionTva().isEmpty()) p.contenu.addView(UiKit.text(a, f.mentionTva(), 14, UiKit.ENCRE_2, false));

        long ttc = Math.max(0, Money.parseCentimes(montant));
        if (!sansTva) {
            String aide = ttc > 0 ? "Dont TVA " + Money.format(ttc - Tva.ht(ttc, tvaBp)) + " · hors TVA " + Money.format(Tva.ht(ttc, tvaBp)) : "";
            p.contenu.addView(UiKit.blocTva(a, "TVA", tvaBp, aide, t -> {
                garder.run();
                tvaBp = t;
                a.remplacer(this);
            }), UiKit.pleineLargeur());

            // Taux réduit : la mention du client (sinon 20 % sur tout).
            if (TvaGuide.tauxReduitBatiment(tvaBp) && ttc >= TvaGuide.SEUIL_SANS_CERTIFICATION_TTC) {
                boolean ok = "oui".equals(certification);
                LinearLayout cert = UiKit.carte(a, 10);
                cert.setBackground(UiKit.rounded(ok ? UiKit.SURFACE : UiKit.AMBRE_FOND, dp(16), 0, 0));
                cert.addView(UiKit.titre(a, "Mention à faire signer au client", 19));
                cert.addView(UiKit.text(a, "Il la recopie et la signe sur le devis ou la facture. Sans elle, c'est 20 % sur tout.", 15, ok ? UiKit.ENCRE_2 : UiKit.AMBRE, !ok));
                String mention = tvaBp == Tva.REDUIT ? TvaGuide.MENTION_DEVIS_55 : TvaGuide.MENTION_DEVIS_10;
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
                cert.addView(UiKit.text(a, "Le client l'a signée ?", 16, UiKit.ENCRE_2, false));
                cert.addView(UiKit.choix(a, new String[][]{{"", "Pas encore"}, {"oui", "Oui, signée"}}, ok ? "oui" : "", choisi -> {
                    garder.run();
                    certification = choisi;
                    a.remplacer(this);
                }), UiKit.pleineLargeur());
                p.contenu.addView(cert, UiKit.pleineLargeur());
            } else if (TvaGuide.tauxReduitBatiment(tvaBp) && ttc > 0) {
                p.contenu.addView(UiKit.text(a, "Moins de 1 000 € TTC d'entretien ou de réparation : pas de mention à signer. Indiquez sur la facture le client, "
                        + "l'adresse du logement, la nature des travaux et « logement achevé depuis plus de 2 ans ».", 15, UiKit.ENCRE_2, false));
            }
        }

        // Tiroir suivant
        LinearLayout tiroir = UiKit.carte(a, 8);
        tiroir.setBackground(UiKit.rounded(UiKit.PIERRE, dp(16), 0, 0));
        tiroir.addView(UiKit.text(a, "Plusieurs ouvrages, plusieurs taux de TVA, un vrai devis ?", 16, UiKit.ENCRE, true));
        tiroir.addView(UiKit.boutonContour(a, "Détailler en lignes ›", v -> {
            garder.run();
            detailler(c, f);
        }), UiKit.pleineLargeur());
        p.contenu.addView(tiroir, UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            if (enregistrerPrix(c, f, fMontant)) {
                toast("Prix enregistré ✓");
                a.retour();
            }
        }));
        return p.racine;
    }

    private boolean enregistrerPrix(Chantier c, Finance f, EditText fMontant) {
        long cts = montant.isEmpty() ? 0 : Money.parseCentimes(montant);
        if (cts < 0) {
            fMontant.setError("Montant invalide");
            return false;
        }
        Map<String, String> data = new LinkedHashMap<>();
        if (cts != c.prixCentimes) data.put("prix_centimes", Long.toString(cts));
        if (!f.sansTvaFacturee() && tvaBp != f.tvaPrix()) data.put("prix_tva_bp", Integer.toString(tvaBp));
        if (!certification.equals(c.certification)) data.put("certification", certification);
        return data.isEmpty() || enregistrer(chantierId, Event.CHANTIER_MODIFIE, data);
    }

    /** Le prix devient la première ligne d'un devis signé, qu'on complète. */
    private void detailler(Chantier c, Finance f) {
        long cts = montant.isEmpty() ? 0 : Math.max(0, Money.parseCentimes(montant));
        Ligne l = new Ligne();
        l.designation = c.titre;
        l.quantiteMilli = 1000;
        l.unite = "forfait";
        l.tvaBp = f.sansTvaFacturee() ? 0 : tvaBp;
        l.puHtCentimes = Tva.ht(cts, l.tvaBp);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("sorte", Chantier.DEVIS_NOUS);
        data.put("libelle", "Devis");
        data.put("lignes", Ligne.encoder(Collections.singletonList(l)));
        data.put("remise_bp", "0");
        data.put("autoliquidation", "non");
        // Brouillon : le prix simple reste valable tant que le devis n'est pas signé (pas de double compte).
        data.put("statut", Chantier.DEVIS_BROUILLON);
        data.put("montant_centimes", Long.toString(cts));
        data.put("version", "1");
        if ("oui".equals(certification)) {
            data.put("certification", "oui");
            data.put("certification_le", Dates.aujourdhui());
        }
        com.gregory.truelle.data.Event e = com.gregory.truelle.data.Event.create(Event.DEVIS, chantierId, a.auteur(), data);
        try {
            store().ajouter(chantierId, e);
            a.apresEnregistrement();
        } catch (Exception ex) {
            toast("Enregistrement impossible : " + ex.getMessage());
            return;
        }
        toast("Devis préparé à partir du prix ✓");
        a.remplacer(new DevisScreen(a, chantierId, e.id));
    }
}
