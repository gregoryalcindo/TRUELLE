package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Heures;
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
 * Heures de main d'œuvre. Pro : coût (heures × taux horaire chargé).
 * Entre proches : main d'œuvre due par le proche (heures × taux convenu).
 */
public final class HeuresScreen extends Screen {

    private static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);

    private final String chantierId;
    private final String heureId;
    private String heures, date, personne, taux, note;

    public HeuresScreen(MainActivity a, String chantierId, String heureId) {
        super(a);
        this.chantierId = chantierId;
        this.heureId = heureId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Heure existante = c != null && heureId != null ? c.heure(heureId) : null;
        boolean edition = existante != null;
        Finance fin = c == null ? null : a.finance(c);
        if (heures == null) {
            heures = edition ? Heures.toInput(existante.heuresCentiemes) : "";
            date = edition && !existante.date.isEmpty() ? existante.date : FORMAT_DATE.format(new Date());
            personne = edition ? existante.personne : a.auteur();
            taux = edition && existante.tauxCentimes >= 0 ? Money.toInput(existante.tauxCentimes) : "";
            note = edition ? existante.note : "";
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, edition ? "Modifier les heures" : "Heures de travail", 30));
        if (c == null) return p.racine;

        EditText fHeures = UiKit.champ(a, heures, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fHeures.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,.h:"));
        fHeures.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fHeures.setTypeface(UiKit.TITRE);
        fHeures.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        fHeures.setHint("Ex. : 7,5");
        EditText fDate = UiKit.champ(a, date, InputType.TYPE_NULL);
        fDate.setFocusable(false);
        fDate.setOnClickListener(v -> choisirDate(fDate));
        EditText fPersonne = UiKit.champ(a, personne, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText fTaux = UiKit.champMontant(a, taux);
        long tauxDefaut = c.entreProches() ? c.tauxConvenuCentimes : fin.r.tauxHoraireCentimes;
        fTaux.setHint(tauxDefaut > 0 ? Money.toInput(tauxDefaut) + " (par défaut)" : "Ex. : 35");
        EditText fNote = UiKit.champTexte(a, note);
        fNote.setHint(c.entreProches() ? "Ex. : pose du carrelage" : "Facultatif");
        Runnable garder = () -> {
            heures = fHeures.getText().toString().trim();
            personne = fPersonne.getText().toString().trim();
            taux = fTaux.getText().toString().trim();
            note = fNote.getText().toString().trim();
        };

        p.contenu.addView(UiKit.libelle(a, "Nombre d'heures", fHeures), UiKit.pleineLargeur());
        LinearLayout duo = UiKit.ligne(a, 10);
        duo.setGravity(Gravity.TOP);
        duo.addView(UiKit.libelle(a, "Qui", fPersonne), UiKit.poids(1.2f));
        duo.addView(UiKit.libelle(a, "Date", fDate), UiKit.poids(1f));
        p.contenu.addView(duo, UiKit.pleineLargeur());
        String libTaux = c.entreProches() ? "Taux horaire convenu" : "Coût horaire chargé";
        p.contenu.addView(UiKit.libelle(a, libTaux + " (€/h)", UiKit.euro(a, fTaux)), UiKit.pleineLargeur());
        if (tauxDefaut <= 0) {
            p.contenu.addView(UiKit.text(a, c.entreProches()
                    ? "Astuce : réglez le taux convenu dans les réglages du chantier pour ne plus le saisir."
                    : "Astuce : réglez le coût horaire chargé dans les réglages de l'appli pour ne plus le saisir.", 15, UiKit.ENCRE_2, false));
        }
        p.contenu.addView(UiKit.libelle(a, c.entreProches() ? "Travail fait" : "Note", fNote), UiKit.pleineLargeur());

        long h = Heures.parse(heures);
        long t = taux.isEmpty() ? tauxDefaut : Money.parseCentimes(taux);
        if (h > 0 && t > 0) {
            String total = Heures.format(h) + " × " + Money.format(t) + " = " + Money.format(com.gregory.truelle.data.Tva.arrondi(h * t, 100));
            p.contenu.addView(UiKit.text(a, (c.entreProches() ? "À payer : " : "Coût : ") + total, 18, UiKit.ENCRE, true));
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            long hc = Heures.parse(heures);
            if (hc <= 0) {
                fHeures.setError("Ex. : 7,5 ou 7h30");
                return;
            }
            long tc = taux.isEmpty() ? -1 : Money.parseCentimes(taux);
            if (!taux.isEmpty() && tc < 0) {
                fTaux.setError("Montant invalide");
                return;
            }
            Map<String, String> data = new LinkedHashMap<>();
            boolean ok;
            if (!edition) {
                data.put("heures_centiemes", Long.toString(hc));
                data.put("date", date);
                data.put("personne", personne);
                if (tc >= 0) data.put("taux_centimes", Long.toString(tc));
                if (!note.isEmpty()) data.put("note", note);
                ok = enregistrer(chantierId, Event.HEURES, data);
            } else {
                data.put("cible", existante.id);
                if (hc != existante.heuresCentiemes) data.put("heures_centiemes", Long.toString(hc));
                if (!date.equals(existante.date)) data.put("date", date);
                if (!personne.equals(existante.personne)) data.put("personne", personne);
                if (tc != existante.tauxCentimes) data.put("taux_centimes", Long.toString(tc));
                if (!note.equals(existante.note)) data.put("note", note);
                ok = data.size() == 1 || enregistrer(chantierId, Event.CORRECTION, data);
            }
            if (ok) {
                toast("Heures enregistrées ✓");
                a.retour();
            }
        }));
        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer ces heures ?")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existante.id))) {
                            toast("Heures supprimées");
                            a.retour();
                        }
                    }).show()));
        } else if (heures.isEmpty()) {
            fHeures.requestFocus();
        }
        return p.racine;
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
