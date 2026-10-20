package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pro : une ligne du suivi de facturation (acompte, situation, solde). Pas une facture légale :
 * on note le numéro de la vraie facture pour faire le lien.
 * Entre proches : un versement du proche.
 */
public final class FacturationScreen extends Screen {

    private static final String ACOMPTE = "acompte";
    private static final String SITUATION = "situation";
    private static final String SOLDE = "solde";

    private final String chantierId;
    private final String ligneId;
    private String type, libelle, montant, statut, numero, pourcentage;
    private int tvaBp = -1;

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
        Finance fin = c == null ? null : a.finance(c);
        boolean proches = c != null && c.entreProches();
        if (libelle == null && c != null) {
            type = edition ? existant.get("type") : (proches ? "versement" : ACOMPTE);
            libelle = edition ? existant.get("libelle") : (proches ? "Versement" : "Acompte");
            montant = edition ? Money.toInput(fin.ttcFacture(existant)) : "";
            statut = edition ? existant.get("statut") : (proches ? Chantier.FACT_ENCAISSE : Chantier.FACT_A_FACTURER);
            numero = edition ? existant.get("numero") : "";
            tvaBp = edition ? fin.tvaFacture(existant) : fin.tvaDefaut();
            pourcentage = edition && !existant.get("pourcentage_bp").isEmpty() ? Tva.libelle((int) existant.centimes("pourcentage_bp")).replace(" %", "") : "";
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, proches ? (edition ? "Versement" : "Nouveau versement") : (edition ? "Ligne de facturation" : "Nouvelle facturation"), 30));
        if (c == null) return p.racine;

        EditText fLibelle = UiKit.champTexte(a, libelle);
        EditText fMontant = UiKit.champMontant(a, montant);
        EditText fNumero = UiKit.champ(a, numero, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        fNumero.setHint("Facultatif");
        EditText fPct = UiKit.champ(a, pourcentage, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fPct.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        fPct.setHint("Ex. : 30");
        Runnable garder = () -> {
            libelle = fLibelle.getText().toString().trim();
            montant = fMontant.getText().toString().trim();
            numero = fNumero.getText().toString().trim();
            pourcentage = fPct.getText().toString().trim();
        };

        long venduTtc = venduTtc(fin);
        if (!proches) {
            p.contenu.addView(UiKit.choix(a, new String[][]{{ACOMPTE, "Acompte"}, {SITUATION, "Situation"}, {SOLDE, "Solde"}}, type, choisi -> {
                garder.run();
                type = choisi;
                libelle = ACOMPTE.equals(choisi) ? "Acompte" : SITUATION.equals(choisi) ? "Situation n°" + (nbSituations(c) + 1) : "Solde";
                if (SOLDE.equals(choisi)) montant = Money.toInput(Math.max(0, venduTtc - dejaFactureTtc(fin, existant)));
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            p.contenu.addView(UiKit.ligneChiffre(a, "Montant du chantier (TTC)", Money.format(venduTtc), false), UiKit.pleineLargeur());
            p.contenu.addView(UiKit.ligneChiffre(a, "Déjà facturé (TTC)", Money.format(dejaFactureTtc(fin, existant)), false), UiKit.pleineLargeur());

            if (ACOMPTE.equals(type) || SITUATION.equals(type)) {
                String lib = ACOMPTE.equals(type) ? "Acompte en % du chantier" : "Avancement cumulé des travaux (%)";
                LinearLayout ligne = UiKit.ligne(a, 8);
                ligne.addView(fPct, UiKit.poids(1));
                ligne.addView(UiKit.boutonContour(a, "Calculer", v -> {
                    garder.run();
                    int bp = Tva.parse(pourcentage);
                    if (bp < 0) {
                        fPct.setError("Entre 0 et 100");
                        return;
                    }
                    long cible = Tva.arrondi(venduTtc * (long) bp, 10000);
                    // Situation : % cumulé, moins ce qui est déjà facturé.
                    long m = SITUATION.equals(type) ? Math.max(0, cible - dejaFactureTtc(fin, existant)) : cible;
                    montant = Money.toInput(m);
                    a.remplacer(this);
                }), UiKit.poids(1));
                p.contenu.addView(UiKit.libelle(a, lib, ligne), UiKit.pleineLargeur());
            }
        }

        p.contenu.addView(UiKit.libelle(a, "Intitulé", fLibelle), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, proches ? "Montant versé" : "Montant TTC", UiKit.euro(a, fMontant)), UiKit.pleineLargeur());

        if (!proches) {
            long ttc = Math.max(0, Money.parseCentimes(montant));
            String aide = ttc > 0 ? "Dont TVA " + Money.format(ttc - Tva.ht(ttc, tvaBp)) : "";
            if (c.retenueBp > 0 && ttc > 0) {
                long ret = Tva.arrondi(ttc * (long) c.retenueBp, 10000);
                aide += (aide.isEmpty() ? "" : " · ") + "retenue de garantie " + Tva.libelle(c.retenueBp) + " : " + Money.format(ret)
                        + " · net à payer " + Money.format(ttc - ret);
            }
            p.contenu.addView(UiKit.blocTva(a, "TVA", tvaBp, aide, t -> {
                garder.run();
                tvaBp = t;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        }

        LinearLayout blocStatut = UiKit.colonne(a, 8);
        blocStatut.addView(UiKit.text(a, "Statut", 16, UiKit.ENCRE_2, false));
        String[][] options = proches
                ? new String[][]{{Chantier.FACT_FACTURE, "Prévu"}, {Chantier.FACT_ENCAISSE, "Reçu"}}
                : new String[][]{{Chantier.FACT_A_FACTURER, "À facturer"}, {Chantier.FACT_FACTURE, "Facturé"}, {Chantier.FACT_ENCAISSE, "Encaissé"}};
        blocStatut.addView(UiKit.choix(a, options, statut, choisi -> {
            garder.run();
            statut = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        p.contenu.addView(blocStatut, UiKit.pleineLargeur());
        if (!proches) p.contenu.addView(UiKit.libelle(a, "N° de la vraie facture", fNumero), UiKit.pleineLargeur());

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
            int bp = proches ? 0 : Math.max(0, tvaBp);
            long ht = Tva.ht(centimes, bp);
            String aujourdhui = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
            Map<String, String> data = new LinkedHashMap<>();
            boolean ok;
            if (!edition) {
                data.put("type", type);
                data.put("libelle", libelle);
                data.put("montant_centimes", Long.toString(centimes));
                data.put("ht_centimes", Long.toString(ht));
                data.put("tva_bp", Integer.toString(bp));
                data.put("retenue_bp", Integer.toString(proches ? 0 : c.retenueBp));
                int pct = Tva.parse(pourcentage);
                if (pct >= 0 && !pourcentage.isEmpty()) data.put("pourcentage_bp", Integer.toString(pct));
                data.put("statut", statut);
                if (!numero.isEmpty()) data.put("numero", numero);
                if (Finance.facturee(fakeStatut(statut))) data.put("date_facture", aujourdhui);
                if (Chantier.FACT_ENCAISSE.equals(statut)) data.put("date_encaisse", aujourdhui);
                ok = enregistrer(chantierId, Event.FACTURATION, data);
            } else {
                data.put("cible", existant.id);
                if (!libelle.equals(existant.get("libelle"))) data.put("libelle", libelle);
                if (!type.equals(existant.get("type"))) data.put("type", type);
                if (ht != fin.htFacture(existant) || bp != fin.tvaFacture(existant) || existant.get("ht_centimes").isEmpty()) {
                    data.put("montant_centimes", Long.toString(centimes));
                    data.put("ht_centimes", Long.toString(ht));
                    data.put("tva_bp", Integer.toString(bp));
                }
                if (!statut.equals(existant.get("statut"))) {
                    data.put("statut", statut);
                    if (Finance.facturee(fakeStatut(statut)) && existant.get("date_facture").isEmpty()) data.put("date_facture", aujourdhui);
                    if (Chantier.FACT_ENCAISSE.equals(statut)) data.put("date_encaisse", aujourdhui);
                }
                if (!numero.equals(existant.get("numero"))) data.put("numero", numero);
                ok = data.size() == 1 || enregistrer(chantierId, Event.CORRECTION, data);
            }
            if (ok) {
                toast(proches ? "Versement enregistré ✓" : "Ligne enregistrée ✓");
                a.retour();
            }
        }));
        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer cette ligne ?")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existant.id))) {
                            toast("Ligne supprimée");
                            a.retour();
                        }
                    }).show()));
        }
        return p.racine;
    }

    /** Montant client TTC : CA validé + TVA facturée au taux du chantier. */
    private static long venduTtc(Finance f) {
        com.gregory.truelle.data.RecapFin r = com.gregory.truelle.data.RecapFin.construire(f, false);
        return r.totalTtc;
    }

    private static long dejaFactureTtc(Finance f, Chantier.Element sauf) {
        long t = 0;
        for (Chantier.Element el : f.c.elements(Event.FACTURATION)) {
            if (sauf != null && el.id.equals(sauf.id)) continue;
            if (Finance.facturee(el)) t += f.ttcFacture(el);
        }
        return t;
    }

    private static int nbSituations(Chantier c) {
        int n = 0;
        for (Chantier.Element el : c.elements(Event.FACTURATION)) if (SITUATION.equals(el.get("type"))) n++;
        return n;
    }

    /** Petit élément factice pour réutiliser Finance.facturee sur un statut. */
    private static Chantier.Element fakeStatut(String statut) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("statut", statut);
        return Chantier.Element.temporaire(m);
    }
}
