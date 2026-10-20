package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Dates;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Planning de la semaine : qui fait quoi, où, chaque jour.
 * Il s'appuie sur les tâches datées de tous les chantiers.
 */
public final class PlanningScreen extends Screen {

    private LocalDate jour = Dates.aujourdhuiDate();

    public PlanningScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean suitLaSynchro() {
        return true;
    }

    @Override
    public View build() {
        List<Chantier> tous = store().chantiers();
        LocalDate lundi = Dates.lundi(jour);
        Page p = new Page(a);

        LinearLayout haut = UiKit.ligne(a, 6);
        LinearLayout titres = UiKit.colonne(a, 2);
        titres.addView(UiKit.text(a, "Semaine du " + lundi.getDayOfMonth() + " " + Dates.mois(lundi.getMonthValue()), 15, UiKit.ENCRE_2, false));
        titres.addView(UiKit.titre(a, "Planning", 32));
        haut.addView(titres, UiKit.poids(1));
        haut.addView(fleche(R.drawable.ic_back, "Semaine précédente", v -> {
            jour = jour.minusWeeks(1);
            a.remplacer(this);
        }));
        View suiv = fleche(R.drawable.ic_chevron, "Semaine suivante", v -> {
            jour = jour.plusWeeks(1);
            a.remplacer(this);
        });
        haut.addView(suiv);
        p.entete.addView(haut, UiKit.pleineLargeur());

        // Les jours de la semaine, avec un point quand quelque chose est prévu.
        LinearLayout jours = UiKit.ligne(a, 5);
        for (int i = 0; i < 7; i++) {
            LocalDate d = lundi.plusDays(i);
            jours.addView(caseJour(d, aDesTaches(tous, d)), UiKit.poids(1));
        }
        LinearLayout.LayoutParams lpJ = UiKit.pleineLargeur();
        lpJ.topMargin = dp(6);
        p.entete.addView(jours, lpJ);

        // Le jour choisi
        boolean auj = jour.equals(Dates.aujourdhuiDate());
        p.contenu.addView(Blocs.titreSection(a, auj ? "Aujourd'hui" : capitaliser(Dates.long_(jour)), null, null), UiKit.pleineLargeur());
        Blocs.Groupe g = new Blocs.Groupe(a);
        for (Chantier c : tous) {
            for (Chantier.Element t : c.taches()) {
                if (!Dates.memeJour(t.get("date"), jour)) continue;
                g.ajouter(Blocs.rangeeTache(a, t, c.titre, v -> Blocs.basculerTache(a, c.id, t, () -> a.remplacer(this)),
                        v -> a.ouvrir(new TacheScreen(a, c.id, t.id, null))));
            }
        }
        if (g.vide()) p.contenu.addView(UiKit.text(a, "Rien de prévu ce jour-là.", 16, UiKit.ENCRE_2, false));
        else p.contenu.addView(g.vue, UiKit.pleineLargeur());

        // En retard (jours passés, pas faites) : visibles depuis aujourd'hui.
        if (auj) {
            Blocs.Groupe r = new Blocs.Groupe(a);
            for (Chantier c : tous) for (Chantier.Element t : c.taches()) {
                if (Chantier.tacheFaite(t) || !Dates.passee(t.get("date"))) continue;
                r.ajouter(Blocs.rangeeTache(a, t, c.titre, v -> Blocs.basculerTache(a, c.id, t, () -> a.remplacer(this)),
                        v -> a.ouvrir(new TacheScreen(a, c.id, t.id, null))));
            }
            if (!r.vide()) {
                p.contenu.addView(Blocs.titreSection(a, "En retard", null, null), UiKit.pleineLargeur());
                p.contenu.addView(r.vue, UiKit.pleineLargeur());
            }
        }

        // Sans date
        Blocs.Groupe s = new Blocs.Groupe(a);
        for (Chantier c : tous) for (Chantier.Element t : c.taches()) {
            if (Chantier.tacheFaite(t) || !t.get("date").isEmpty() || Chantier.TERMINE.equals(c.statut)) continue;
            s.ajouter(Blocs.rangeeTache(a, t, c.titre, v -> Blocs.basculerTache(a, c.id, t, () -> a.remplacer(this)),
                    v -> a.ouvrir(new TacheScreen(a, c.id, t.id, null))));
        }
        if (!s.vide()) {
            p.contenu.addView(Blocs.titreSection(a, "Sans date", null, null), UiKit.pleineLargeur());
            p.contenu.addView(s.vue, UiKit.pleineLargeur());
        }

        TextView planifier = UiKit.text(a, "+ Planifier une tâche ce jour-là", 16, UiKit.ENCRE_2, true);
        planifier.setGravity(Gravity.CENTER);
        planifier.setMinHeight(dp(52));
        planifier.setBackground(UiKit.ripple(UiKit.rounded(Color.TRANSPARENT, dp(16), dp(2), UiKit.BORDURE)));
        planifier.setOnClickListener(v -> a.ouvrir(new TacheScreen(a, null, null, Dates.format(jour))));
        p.contenu.addView(planifier, UiKit.pleineLargeur());

        p.barre(a, MainActivity.ONGLET_PLANNING);
        return p.racine;
    }

    private static boolean aDesTaches(List<Chantier> tous, LocalDate d) {
        for (Chantier c : tous) for (Chantier.Element t : c.elements(Event.TACHE)) if (Dates.memeJour(t.get("date"), d)) return true;
        return false;
    }

    private View caseJour(LocalDate d, boolean point) {
        boolean sel = d.equals(jour);
        boolean dimanche = d.getDayOfWeek().getValue() >= 6;
        LinearLayout col = UiKit.colonne(a, 2);
        col.setGravity(Gravity.CENTER);
        col.setPadding(0, dp(8), 0, dp(6));
        col.setBackground(UiKit.ripple(UiKit.rounded(sel ? UiKit.ENCRE : dimanche ? UiKit.PIERRE : UiKit.SURFACE, dp(14), 0, 0)));
        col.addView(UiKit.text(a, Dates.jourCourt(d), 12, sel ? Color.parseColor("#C9CCD0") : UiKit.ENCRE_2, false));
        col.addView(UiKit.text(a, Integer.toString(d.getDayOfMonth()), 18, sel ? Color.WHITE : UiKit.ENCRE, true));
        View pt = new View(a);
        pt.setBackground(UiKit.rounded(point ? (sel ? Color.WHITE : UiKit.ENCRE) : Color.TRANSPARENT, dp(3), 0, 0));
        col.addView(pt, new LinearLayout.LayoutParams(dp(6), dp(6)));
        for (int i = 0; i < col.getChildCount(); i++) ((LinearLayout.LayoutParams) col.getChildAt(i).getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        col.setClickable(true);
        col.setContentDescription(Dates.long_(d) + (point ? ", quelque chose de prévu" : ""));
        col.setOnClickListener(v -> {
            jour = d;
            a.remplacer(this);
        });
        return col;
    }

    private View fleche(int icone, String desc, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(22), dp(1), UiKit.BORDURE)));
        f.addView(UiKit.icone(a, icone, UiKit.ENCRE, 20), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        f.setClickable(true);
        f.setContentDescription(desc);
        f.setOnClickListener(l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return f;
    }

    private static String capitaliser(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
