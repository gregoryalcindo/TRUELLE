package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Dates;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Une tâche : quoi, qui, quand. Depuis le Planning, on choisit aussi le chantier. */
public final class TacheScreen extends Screen {

    private String chantierId;
    private final String tacheId;
    private String texte, qui, date;
    private boolean initialise;

    /** @param dateParDefaut « jj/mm/aaaa » proposée pour une nouvelle tâche, ou null (aujourd'hui) */
    public TacheScreen(MainActivity a, String chantierId, String tacheId, String dateParDefaut) {
        super(a);
        this.chantierId = chantierId;
        this.tacheId = tacheId;
        this.date = dateParDefaut;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        List<Chantier> tous = store().chantiers();
        if (chantierId == null) {
            String dernier = a.dernierChantier();
            for (Chantier c : tous) if (c.id.equals(dernier) && !Chantier.TERMINE.equals(c.statut)) chantierId = c.id;
            if (chantierId == null) for (Chantier c : tous) if (Chantier.EN_COURS.equals(c.statut)) {
                chantierId = c.id;
                break;
            }
            if (chantierId == null && !tous.isEmpty()) chantierId = tous.get(0).id;
        }
        Chantier c = chantierId == null ? null : store().charger(chantierId);
        Chantier.Element existante = null;
        if (c != null && tacheId != null) for (Chantier.Element t : c.elements(Event.TACHE)) if (t.id.equals(tacheId)) existante = t;
        boolean edition = existante != null;
        if (!initialise) {
            initialise = true;
            texte = edition ? existante.get("texte") : "";
            qui = edition ? existante.get("qui") : "";
            if (edition) date = existante.get("date");
            else if (date == null) date = Dates.aujourdhui();
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, edition ? "Tâche" : "Nouvelle tâche", 30));
        if (c == null) {
            p.contenu.addView(UiKit.text(a, "Créez d'abord un chantier.", 17, UiKit.ENCRE_2, false));
            return p.racine;
        }

        EditText fTexte = UiKit.champ(a, texte, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fTexte.setHint("Ex. : poser la faïence murale");
        fTexte.setGravity(Gravity.TOP | Gravity.START);
        fTexte.setMinLines(2);
        Runnable garder = () -> texte = fTexte.getText().toString().trim();

        // Chantier (choix seulement pour une nouvelle tâche)
        if (!edition && tacheId == null) {
            List<String[]> opts = new ArrayList<>();
            for (Chantier x : tous) if (!Chantier.TERMINE.equals(x.statut) || x.id.equals(chantierId)) opts.add(new String[]{x.id, x.titre});
            if (opts.size() > 1) {
                LinearLayout bloc = UiKit.colonne(a, 8);
                bloc.addView(UiKit.text(a, "Chantier", 16, UiKit.ENCRE_2, false));
                bloc.addView(puces(opts, chantierId, id -> {
                    garder.run();
                    chantierId = id;
                    a.remplacer(this);
                }), UiKit.pleineLargeur());
                p.contenu.addView(bloc, UiKit.pleineLargeur());
            } else {
                p.contenu.addView(UiKit.text(a, c.titre, 17, UiKit.ENCRE_2, true));
            }
        } else {
            p.contenu.addView(UiKit.text(a, c.titre, 17, UiKit.ENCRE_2, true));
        }

        p.contenu.addView(UiKit.libelle(a, "À faire", fTexte), UiKit.pleineLargeur());

        // Qui
        Set<String> gens = new LinkedHashSet<>();
        if (!a.auteur().isEmpty()) gens.add(a.auteur());
        for (Chantier x : tous) {
            for (Chantier.Element t : x.elements(Event.TACHE)) if (!t.get("qui").isEmpty()) gens.add(t.get("qui"));
            for (Chantier.Depense d : x.depenses()) if (!d.auteur.isEmpty()) gens.add(d.auteur);
        }
        if (!qui.isEmpty()) gens.add(qui);
        List<String[]> optsQui = new ArrayList<>();
        optsQui.add(new String[]{"", "Personne"});
        for (String g : gens) optsQui.add(new String[]{g, g});
        LinearLayout blocQui = UiKit.colonne(a, 8);
        blocQui.addView(UiKit.text(a, "Qui", 16, UiKit.ENCRE_2, false));
        blocQui.addView(puces(optsQui, qui, choisi -> {
            garder.run();
            qui = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        p.contenu.addView(blocQui, UiKit.pleineLargeur());

        // Quand
        LocalDate auj = Dates.aujourdhuiDate();
        String sAuj = Dates.format(auj), sDemain = Dates.format(auj.plusDays(1));
        List<String[]> optsQuand = new ArrayList<>();
        optsQuand.add(new String[]{sAuj, "Aujourd'hui"});
        optsQuand.add(new String[]{sDemain, "Demain"});
        if (!date.isEmpty() && !date.equals(sAuj) && !date.equals(sDemain)) optsQuand.add(new String[]{date, Dates.relatif(date)});
        optsQuand.add(new String[]{"?", "Autre jour…"});
        optsQuand.add(new String[]{"", "Sans date"});
        LinearLayout blocQuand = UiKit.colonne(a, 8);
        blocQuand.addView(UiKit.text(a, "Quand", 16, UiKit.ENCRE_2, false));
        blocQuand.addView(puces(optsQuand, date, choisi -> {
            garder.run();
            if ("?".equals(choisi)) {
                LocalDate d = Dates.parse(date);
                if (d == null) d = auj;
                new DatePickerDialog(a, (vw, y, m, j) -> {
                    date = Dates.format(LocalDate.of(y, m + 1, j));
                    a.remplacer(this);
                }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
                return;
            }
            date = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        p.contenu.addView(blocQuand, UiKit.pleineLargeur());

        final Chantier.Element ex = existante;
        final String cid = chantierId;
        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            if (texte.isEmpty()) {
                fTexte.setError("Indiquez ce qu'il y a à faire");
                return;
            }
            Map<String, String> data = new LinkedHashMap<>();
            boolean ok;
            if (ex == null) {
                data.put("texte", texte);
                data.put("qui", qui);
                data.put("date", date);
                data.put("fait", "non");
                ok = enregistrer(cid, Event.TACHE, data);
            } else {
                data.put("cible", ex.id);
                if (!texte.equals(ex.get("texte"))) data.put("texte", texte);
                if (!qui.equals(ex.get("qui"))) data.put("qui", qui);
                if (!date.equals(ex.get("date"))) data.put("date", date);
                ok = data.size() == 1 || enregistrer(cid, Event.CORRECTION, data);
            }
            if (ok) {
                a.noterDernierChantier(cid);
                toast(ex == null ? "Tâche ajoutée ✓" : "Tâche enregistrée ✓");
                a.retour();
            }
        }));
        if (edition) {
            boolean fait = Chantier.tacheFaite(existante);
            p.ajouterAuPied(UiKit.boutonContour(a, fait ? "Remettre à faire" : "Marquer comme faite",
                    v -> Blocs.basculerTache(a, cid, ex, a::retour)));
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer la tâche", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer cette tâche ?")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(cid, Event.SUPPRESSION, Collections.singletonMap("cible", ex.id))) {
                            toast("Tâche supprimée");
                            a.retour();
                        }
                    }).show()));
        }
        if (texte.isEmpty()) fTexte.requestFocus();
        return p.racine;
    }

    /** Puces de choix qui passent à la ligne (noms, chantiers, jours). */
    private View puces(List<String[]> options, String selection, java.util.function.Consumer<String> onChoix) {
        LinearLayout col = UiKit.colonne(a, 8);
        LinearLayout ligne = null;
        int largeur = 0;
        for (String[] o : options) {
            int estime = 24 + o[1].length() * 9;
            if (ligne == null || largeur + estime > 320) {
                ligne = UiKit.ligne(a, 8);
                col.addView(ligne);
                largeur = 0;
            }
            largeur += estime + 8;
            boolean sel = o[0].equals(selection);
            android.widget.Button b = UiKit.boutonChoix(a, o[1], sel, UiKit.ENCRE, android.graphics.Color.WHITE, v -> onChoix.accept(o[0]));
            b.setPadding(dp(14), 0, dp(14), 0);
            ligne.addView(b);
        }
        return col;
    }
}
