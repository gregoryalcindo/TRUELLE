package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Écran 2 des maquettes : la fiche chantier, avec le gros bouton +. */
public final class FicheScreen extends Screen {

    private final String chantierId;

    public FicheScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Mes chantiers", v -> a.retour()));
        if (c == null) {
            p.entete.addView(UiKit.titre(a, "Chantier introuvable", 30));
            return p.racine;
        }

        LinearLayout ligneTitre = UiKit.ligne(a, 12);
        ligneTitre.addView(UiKit.titre(a, c.titre, 32), UiKit.poids(1));
        ligneTitre.addView(UiKit.chipContour(a, Chantier.MODE_REEL.equals(c.mode) ? "Réel" : "Forfait"));
        p.entete.addView(ligneTitre, UiKit.pleineLargeur());

        String sous = c.client;
        if (!c.adresse.isEmpty()) sous = sous.isEmpty() ? c.adresse : sous + " · " + c.adresse;
        if (!sous.isEmpty()) p.entete.addView(UiKit.text(a, sous, 18, UiKit.ENCRE_2, false));

        p.contenu.addView(blocStatut(c), UiKit.pleineLargeur());

        LinearLayout ligneAjouts = UiKit.ligne(a, 8);
        ligneAjouts.addView(UiKit.titre(a, "Derniers ajouts", 20), UiKit.poids(1));
        if (!c.depenses().isEmpty()) {
            ligneAjouts.addView(UiKit.text(a, "Dépenses : " + Money.format(c.totalDepensesCentimes()), 16, UiKit.ENCRE, true));
        }
        p.contenu.addView(ligneAjouts, UiKit.pleineLargeur());

        List<Chantier.Ajout> ajouts = c.derniersAjouts();
        if (ajouts.isEmpty()) {
            p.contenu.addView(UiKit.text(a, "Rien pour l'instant. Touchez + pour ajouter une dépense ou une note.", 17, UiKit.ENCRE_2, false));
        }
        for (Chantier.Ajout aj : ajouts) p.contenu.addView(ligneAjout(c, aj), UiKit.pleineLargeur());

        p.pied.addView(boutonPlus(c));
        return p.racine;
    }

    private View blocStatut(Chantier c) {
        LinearLayout bloc = UiKit.colonne(a, 8);
        bloc.addView(UiKit.text(a, "Statut — toucher pour changer", 16, UiKit.ENCRE_2, false));
        LinearLayout row = UiKit.ligne(a, 8);
        String[] statuts = {Chantier.A_DEMARRER, Chantier.EN_COURS, Chantier.TERMINE};
        for (String s : statuts) {
            int[] col = UiKit.couleursStatut(s);
            row.addView(UiKit.boutonChoix(a, Chantier.libelleStatut(s), s.equals(c.statut), col[0], col[1], v -> {
                if (s.equals(c.statut)) return;
                if (enregistrer(c.id, Event.STATUT, Collections.singletonMap("valeur", s))) {
                    toast("Statut : " + Chantier.libelleStatut(s));
                    a.remplacer(new FicheScreen(a, c.id));
                }
            }), UiKit.poids(1));
        }
        bloc.addView(row, UiKit.pleineLargeur());
        return bloc;
    }

    private View ligneAjout(Chantier c, Chantier.Ajout aj) {
        LinearLayout row = UiKit.ligne(a, 14);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(14), 0, 0)));
        row.setClickable(true);

        LinearLayout textes = UiKit.colonne(a, 2);
        String titreLigne;
        String montant = null;
        String auteur;
        if (Event.DEPENSE.equals(aj.type)) {
            Chantier.Depense d = c.depense(aj.id);
            row.addView(UiKit.pastille(a, R.drawable.ic_receipt));
            titreLigne = d.fournisseur.isEmpty() ? "Dépense" : "Dépense · " + d.fournisseur;
            montant = Money.format(d.montantCentimes);
            auteur = d.auteur;
            row.setOnClickListener(v -> a.ouvrir(new DepenseScreen(a, c.id, d.id)));
        } else {
            Chantier.Note n = c.note(aj.id);
            row.addView(UiKit.pastille(a, R.drawable.ic_note));
            String t = n.texte.replace('\n', ' ');
            titreLigne = t.length() > 40 ? t.substring(0, 40) + "…" : t;
            auteur = n.auteur;
            row.setOnClickListener(v -> a.ouvrir(new NoteScreen(a, c.id, n.id)));
        }
        TextView t1 = UiKit.text(a, titreLigne, 18, UiKit.ENCRE, true);
        t1.setMaxLines(2);
        textes.addView(t1);
        textes.addView(UiKit.text(a, quand(aj.at) + (auteur.isEmpty() ? "" : " · " + auteur), 16, UiKit.ENCRE_2, false));
        row.addView(textes, UiKit.poids(1));
        if (montant != null) row.addView(UiKit.text(a, montant, 18, UiKit.ENCRE, true));
        row.setContentDescription(titreLigne + (montant != null ? ", " + montant : ""));
        return row;
    }

    private View boutonPlus(Chantier c) {
        FrameLayout cadre = new FrameLayout(a);
        FrameLayout rond = new FrameLayout(a);
        rond.setBackground(UiKit.ripple(UiKit.rounded(UiKit.ACTION, dp(44), 0, 0)));
        rond.setElevation(dp(4));
        rond.setClickable(true);
        rond.setContentDescription("Ajouter une dépense ou une note");
        ImageView plus = UiKit.icone(a, R.drawable.ic_plus, Color.WHITE, 44);
        rond.addView(plus, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        rond.setOnClickListener(v -> a.ouvrir(new AjouterScreen(a, c.id)));
        cadre.addView(rond, new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER_HORIZONTAL));
        cadre.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        return cadre;
    }

    /** « Aujourd'hui », « Hier », ou la date. */
    static String quand(long at) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(at);
        boolean memeAnnee = now.get(Calendar.YEAR) == then.get(Calendar.YEAR);
        int diff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR);
        if (memeAnnee && diff == 0) return "Aujourd'hui";
        if (memeAnnee && diff == 1) return "Hier";
        return new SimpleDateFormat(memeAnnee ? "d MMM" : "d MMM yyyy", Locale.FRANCE).format(new Date(at));
    }
}
