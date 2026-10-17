package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Écran 4 des maquettes : saisie d'une dépense (lot 1 : saisie manuelle ;
 * la photo du ticket avec lecture automatique arrive au lot 2).
 * Avec un identifiant de dépense, l'écran sert à la corriger ou la supprimer.
 */
public final class DepenseScreen extends Screen {

    private static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);

    private final String chantierId;
    private final String depenseId;

    // Saisie en cours, gardée si l'écran est redessiné.
    private String montant;
    private String fournisseur;
    private String date;
    private String refacturer;

    public DepenseScreen(MainActivity a, String chantierId, String depenseId) {
        super(a);
        this.chantierId = chantierId;
        this.depenseId = depenseId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Depense existante = (c != null && depenseId != null) ? c.depense(depenseId) : null;
        boolean edition = existante != null;

        if (montant == null) {
            montant = edition ? Money.toInput(existante.montantCentimes) : "";
            fournisseur = edition ? existante.fournisseur : "";
            date = edition && !existante.date.isEmpty() ? existante.date : FORMAT_DATE.format(new Date());
            refacturer = edition ? existante.refacturer : "auto";
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, edition ? "Modifier la dépense" : "Nouvelle dépense", 30));
        if (c == null) return p.racine;

        EditText fMontant = UiKit.champ(a, montant, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fMontant.setTypeface(UiKit.TITRE);
        fMontant.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        fMontant.setMinHeight(dp(64));
        fMontant.setHint("0,00 €");
        // Le clavier numérique de certains téléphones n'a que le point : on accepte aussi la virgule.
        fMontant.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        fMontant.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        EditText fFournisseur = UiKit.champ(a, fournisseur, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fFournisseur.setHint("Ex. : Point P");

        EditText fDate = UiKit.champ(a, date, InputType.TYPE_NULL);
        fDate.setFocusable(false);
        fDate.setOnClickListener(v -> choisirDate(fDate));

        p.contenu.addView(UiKit.libelle(a, "Montant TTC", fMontant), UiKit.pleineLargeur());
        LinearLayout duo = UiKit.ligne(a, 10);
        duo.setGravity(android.view.Gravity.TOP);
        duo.addView(UiKit.libelle(a, "Fournisseur", fFournisseur), UiKit.poids(1.4f));
        duo.addView(UiKit.libelle(a, "Date", fDate), UiKit.poids(1f));
        p.contenu.addView(duo, UiKit.pleineLargeur());

        p.contenu.addView(UiKit.text(a, "Chantier : " + c.titre + (c.client.isEmpty() ? "" : " · " + c.client), 16, UiKit.ENCRE_2, false));

        if (edition) {
            // Niveau « poussé » : visible seulement quand on revient corriger une dépense.
            p.contenu.addView(blocRefacturer(c, fMontant, fFournisseur), UiKit.pleineLargeur());
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Valider", v -> {
            garder(fMontant, fFournisseur);
            long centimes = Money.parseCentimes(montant);
            if (centimes <= 0) {
                fMontant.setError("Indiquez un montant, par ex. 45,90");
                fMontant.requestFocus();
                return;
            }
            boolean ok;
            if (!edition) {
                Map<String, String> data = new LinkedHashMap<>();
                data.put("montant_centimes", Long.toString(centimes));
                data.put("fournisseur", fournisseur);
                data.put("date", date);
                data.put("refacturer", "auto");
                ok = enregistrer(chantierId, Event.DEPENSE, data);
            } else {
                // On n'enregistre que ce qui a changé.
                Map<String, String> data = new LinkedHashMap<>();
                data.put("cible", existante.id);
                if (centimes != existante.montantCentimes) data.put("montant_centimes", Long.toString(centimes));
                if (!fournisseur.equals(existante.fournisseur)) data.put("fournisseur", fournisseur);
                if (!date.equals(existante.date)) data.put("date", date);
                if (!refacturer.equals(existante.refacturer)) data.put("refacturer", refacturer);
                ok = data.size() == 1 || enregistrer(chantierId, Event.CORRECTION, data);
            }
            if (ok) {
                toast("Dépense enregistrée ✓");
                a.retour();
            }
        }));

        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer cette dépense", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer cette dépense ?")
                    .setMessage(Money.format(existante.montantCentimes) + (existante.fournisseur.isEmpty() ? "" : " · " + existante.fournisseur))
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existante.id))) {
                            toast("Dépense supprimée");
                            a.retour();
                        }
                    })
                    .show()));
        } else if (montant.isEmpty()) {
            fMontant.requestFocus();
            fMontant.post(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(fMontant, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
        }
        return p.racine;
    }

    private View blocRefacturer(Chantier c, EditText fMontant, EditText fFournisseur) {
        LinearLayout bloc = UiKit.colonne(a, 8);
        String defaut = Chantier.MODE_REEL.equals(c.mode) ? "oui" : "non";
        bloc.addView(UiKit.text(a, "Refacturer au client (chantier « " + (Chantier.MODE_REEL.equals(c.mode) ? "Réel" : "Forfait") + " » : " + defaut + " par défaut)", 16, UiKit.ENCRE_2, false));
        LinearLayout row = UiKit.ligne(a, 8);
        String[][] options = {{"auto", "Selon le chantier"}, {"oui", "Oui"}, {"non", "Non"}};
        for (String[] o : options) {
            row.addView(UiKit.boutonChoix(a, o[1], o[0].equals(refacturer), UiKit.ENCRE, Color.WHITE, v -> {
                garder(fMontant, fFournisseur);
                refacturer = o[0];
                a.remplacer(this);
            }), o[0].equals("auto") ? UiKit.poids(1.6f) : UiKit.poids(1f));
        }
        bloc.addView(row, UiKit.pleineLargeur());
        return bloc;
    }

    private void garder(EditText fMontant, EditText fFournisseur) {
        montant = fMontant.getText().toString().trim();
        fournisseur = fFournisseur.getText().toString().trim();
    }

    private void choisirDate(EditText fDate) {
        Calendar cal = Calendar.getInstance();
        try {
            Date d = FORMAT_DATE.parse(date);
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {
        }
        new DatePickerDialog(a, (view, y, m, day) -> {
            Calendar choisi = Calendar.getInstance();
            choisi.set(y, m, day);
            date = FORMAT_DATE.format(choisi.getTime());
            fDate.setText(date);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }
}
