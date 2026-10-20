package com.gregory.truelle.ui.screens;

import android.app.DatePickerDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Dates;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.RecapFin;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * « Le client m'a payé » : un montant, une date. C'est tout.
 * Tiroir suivant : la facturation détaillée (acompte, avancement, solde, retenue de garantie).
 */
public final class PaiementScreen extends Screen {

    private final String chantierId;
    private String montant = "";
    private String date = Dates.aujourdhui();

    public PaiementScreen(MainActivity a, String chantierId) {
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
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Le client m'a payé", 30));
        if (c == null) return p.racine;
        Finance f = a.finance(c);

        long total = RecapFin.construire(f, false).totalTtc;
        long reste = Math.max(0, total - f.encaisse() - f.retenues());
        if (total > 0) {
            p.contenu.addView(UiKit.ligneChiffre(a, "Déjà payé", Money.format(f.encaisse()), false), UiKit.pleineLargeur());
            p.contenu.addView(UiKit.ligneChiffre(a, "Reste à payer", Money.format(reste), true), UiKit.pleineLargeur());
        }

        EditText fMontant = UiKit.champMontant(a, montant);
        fMontant.setHint(reste > 0 ? Money.toInput(reste) : "Ex. : 500");
        p.contenu.addView(UiKit.libelle(a, "Montant reçu", UiKit.euro(a, fMontant)), UiKit.pleineLargeur());
        if (reste > 0) {
            p.contenu.addView(UiKit.boutonContour(a, "Tout le reste : " + Money.format(reste), v -> {
                montant = Money.toInput(reste);
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        }
        EditText fDate = UiKit.champ(a, date, InputType.TYPE_NULL);
        fDate.setFocusable(false);
        fDate.setOnClickListener(v -> {
            montant = fMontant.getText().toString().trim();
            LocalDate d = Dates.parse(date);
            if (d == null) d = Dates.aujourdhuiDate();
            new DatePickerDialog(a, (vw, y, m, j) -> {
                date = Dates.format(LocalDate.of(y, m + 1, j));
                a.remplacer(this);
            }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
        });
        p.contenu.addView(UiKit.libelle(a, "Le", fDate), UiKit.pleineLargeur());

        // Tiroir suivant
        LinearLayout tiroir = UiKit.carte(a, 8);
        tiroir.setBackground(UiKit.rounded(UiKit.PIERRE, dp(16), 0, 0));
        tiroir.addView(UiKit.text(a, "Acompte en %, facture d'avancement, solde, retenue de garantie ?", 16, UiKit.ENCRE, true));
        tiroir.addView(UiKit.boutonContour(a, "Facture détaillée ›", v -> a.remplacer(new FacturationScreen(a, chantierId, null))), UiKit.pleineLargeur());
        p.contenu.addView(tiroir, UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer le paiement", v -> {
            montant = fMontant.getText().toString().trim();
            long cts = Money.parseCentimes(montant);
            if (cts <= 0) {
                fMontant.setError("Indiquez le montant reçu");
                return;
            }
            int bp = f.sansTvaFacturee() ? 0 : f.prixSimpleHt() > 0 ? f.tvaPrix() : f.tvaDefaut();
            Map<String, String> data = new LinkedHashMap<>();
            data.put("type", "paiement");
            data.put("libelle", "Paiement du " + date);
            data.put("montant_centimes", Long.toString(cts));
            data.put("ht_centimes", Long.toString(Tva.ht(cts, bp)));
            data.put("tva_bp", Integer.toString(bp));
            data.put("retenue_bp", "0");
            data.put("statut", Chantier.FACT_ENCAISSE);
            data.put("date_facture", date);
            data.put("date_encaisse", date);
            if (enregistrer(chantierId, Event.FACTURATION, data)) {
                toast("Paiement de " + Money.format(cts) + " enregistré ✓");
                a.retour();
            }
        }));
        return p.racine;
    }
}
