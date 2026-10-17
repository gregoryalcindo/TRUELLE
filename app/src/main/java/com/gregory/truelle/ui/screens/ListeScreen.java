package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Écran 1 des maquettes : « Mes chantiers ». */
public final class ListeScreen extends Screen {

    /** Filtre gardé pendant la session (on revient sur la même liste). */
    private static String filtre = Chantier.EN_COURS;

    public ListeScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean suitLaSynchro() {
        return true;
    }

    @Override
    public View build() {
        Page p = new Page(a);
        String date = new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date());
        p.entete.addView(UiKit.text(a, Character.toUpperCase(date.charAt(0)) + date.substring(1), 18, UiKit.ENCRE_2, false));
        p.entete.addView(UiKit.titre(a, "Mes chantiers", 36));
        p.entete.addView(etatDrive());

        List<Chantier> tous = store().chantiers();
        p.entete.addView(filtres(tous));

        List<Chantier> visibles = new ArrayList<>();
        for (Chantier c : tous) if (filtre.equals(c.statut)) visibles.add(c);

        if (visibles.isEmpty()) {
            String msg = tous.isEmpty()
                    ? "Aucun chantier pour l'instant.\nTouchez « Nouveau chantier » pour commencer."
                    : "Aucun chantier « " + Chantier.libelleStatut(filtre) + " ».";
            TextView vide = UiKit.text(a, msg, 18, UiKit.ENCRE_2, false);
            vide.setPadding(0, dp(24), 0, 0);
            p.contenu.addView(vide);
        }
        for (Chantier c : visibles) p.contenu.addView(carte(c), UiKit.pleineLargeur());

        TextView reglages = UiKit.text(a, "Réglages de l'appli (entreprise, Drive) ›", 16, UiKit.ENCRE, true);
        reglages.setPadding(0, dp(18), 0, dp(6));
        reglages.setMinHeight(dp(48));
        reglages.setClickable(true);
        reglages.setOnClickListener(v -> a.ouvrir(new AppReglagesScreen(a)));
        p.contenu.addView(reglages);

        p.ajouterAuPied(UiKit.boutonAction(a, "+ Nouveau chantier", v -> a.ouvrir(new NouveauChantierScreen(a))));
        return p.racine;
    }

    /** État de la synchro Drive ; un toucher connecte ou relance la synchro. */
    private View etatDrive() {
        com.gregory.truelle.sync.SyncManager sync = a.sync();
        com.gregory.truelle.sync.SyncManager.Etat e = sync.etat();
        boolean action = e == com.gregory.truelle.sync.SyncManager.Etat.NON_CONNECTE || e == com.gregory.truelle.sync.SyncManager.Etat.RECONNEXION
                || e == com.gregory.truelle.sync.SyncManager.Etat.ERREUR;
        int couleur = e == com.gregory.truelle.sync.SyncManager.Etat.ERREUR ? UiKit.ROUGE : action ? UiKit.ACTION : UiKit.ENCRE_2;
        TextView t = UiKit.text(a, sync.resume(), 16, couleur, action);
        t.setMinHeight(dp(36));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setClickable(true);
        t.setOnClickListener(v -> {
            if (!sync.connecte() || sync.etat() == com.gregory.truelle.sync.SyncManager.Etat.RECONNEXION) a.connecterDrive();
            else sync.synchroniser(true);
        });
        return t;
    }

    private View filtres(List<Chantier> tous) {
        String[] statuts = {Chantier.EN_COURS, Chantier.A_DEMARRER, Chantier.TERMINE};
        LinearLayout row = UiKit.ligne(a, 8);
        row.setPadding(0, dp(8), 0, 0);
        for (String s : statuts) {
            int n = 0;
            for (Chantier c : tous) if (s.equals(c.statut)) n++;
            boolean sel = s.equals(filtre);
            Button b = UiKit.bouton(a, Chantier.libelleStatut(s) + " · " + n,
                    sel ? UiKit.ENCRE : Color.TRANSPARENT, sel ? Color.WHITE : UiKit.ENCRE, 48, v -> {
                        filtre = s;
                        a.racine(new ListeScreen(a));
                    });
            b.setTextSize(17);
            b.setTypeface(sel ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            b.setBackground(UiKit.ripple(UiKit.rounded(sel ? UiKit.ENCRE : Color.TRANSPARENT, dp(24), sel ? 0 : dp(2), UiKit.BORDURE)));
            row.addView(b);
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    private View carte(Chantier c) {
        LinearLayout carte = UiKit.carte(a, 10);
        LinearLayout haut = UiKit.ligne(a, 12);
        haut.addView(UiKit.titre(a, c.titre, 24), UiKit.poids(1));
        haut.addView(UiKit.icone(a, R.drawable.ic_chevron, UiKit.ENCRE, 28));
        carte.addView(haut, UiKit.pleineLargeur());

        String sous = c.client;
        if (!c.adresse.isEmpty()) sous = sous.isEmpty() ? c.adresse : sous + " · " + c.adresse;
        if (!sous.isEmpty()) carte.addView(UiKit.text(a, sous, 18, UiKit.ENCRE_2, false));

        int[] col = UiKit.couleursStatut(c.statut);
        TextView chip = UiKit.chip(a, Chantier.libelleStatut(c.statut), col[0], col[1]);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        carte.addView(chip, lp);

        carte.setClickable(true);
        carte.setContentDescription("Chantier " + c.titre + ", " + Chantier.libelleStatut(c.statut));
        carte.setOnClickListener(v -> a.ouvrir(new FicheScreen(a, c.id)));
        return carte;
    }
}
