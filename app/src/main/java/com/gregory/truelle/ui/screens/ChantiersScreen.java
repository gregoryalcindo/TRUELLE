package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Postes;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/** Onglet Chantiers : en cours / à venir / terminés, avec photo, budget et bénéfice. */
public final class ChantiersScreen extends Screen {

    private static String filtre = Chantier.EN_COURS;

    public ChantiersScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean suitLaSynchro() {
        return true;
    }

    @Override
    public View build() {
        Page p = new Page(a);
        LinearLayout haut = UiKit.ligne(a, 8);
        haut.addView(UiKit.titre(a, "Chantiers", 32), UiKit.poids(1));
        Button nouveau = UiKit.boutonContour(a, "+ Nouveau", v -> a.ouvrir(new NouveauChantierScreen(a)));
        nouveau.setMinHeight(dp(48));
        nouveau.setMinimumHeight(dp(48));
        haut.addView(nouveau);
        p.entete.addView(haut, UiKit.pleineLargeur());

        List<Chantier> tous = store().chantiers();
        p.entete.addView(segments(tous), UiKit.pleineLargeur());

        List<Chantier> visibles = new ArrayList<>();
        for (Chantier c : tous) if (filtre.equals(c.statut)) visibles.add(c);
        if (visibles.isEmpty()) {
            TextView vide = UiKit.text(a, tous.isEmpty()
                    ? "Aucun chantier pour l'instant.\nTouchez « + Nouveau » pour commencer."
                    : "Aucun chantier « " + Chantier.libelleStatut(filtre) + " ».", 17, UiKit.ENCRE_2, false);
            vide.setPadding(0, dp(24), 0, 0);
            p.contenu.addView(vide);
        }
        for (Chantier c : visibles) p.contenu.addView(carte(c), UiKit.pleineLargeur());

        p.barre(a, MainActivity.ONGLET_CHANTIERS);
        return p.racine;
    }

    /** Sélecteur segmenté : En cours · À venir · Terminés. */
    private View segments(List<Chantier> tous) {
        LinearLayout row = UiKit.ligne(a, 4);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackground(UiKit.rounded(UiKit.PIERRE, dp(16), 0, 0));
        String[][] s = {{Chantier.EN_COURS, "En cours"}, {Chantier.A_DEMARRER, "À venir"}, {Chantier.TERMINE, "Terminés"}};
        for (String[] o : s) {
            int n = 0;
            for (Chantier c : tous) if (o[0].equals(c.statut)) n++;
            boolean sel = o[0].equals(filtre);
            Button b = UiKit.bouton(a, o[1] + " · " + n, sel ? UiKit.SURFACE : Color.TRANSPARENT, sel ? UiKit.ENCRE : UiKit.ENCRE_2, 44, v -> {
                filtre = o[0];
                a.remplacer(this);
            });
            b.setTextSize(15);
            b.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setBackground(UiKit.ripple(UiKit.rounded(sel ? UiKit.SURFACE : Color.TRANSPARENT, dp(12), 0, 0)));
            if (sel) b.setElevation(dp(1));
            b.setPadding(dp(4), 0, dp(4), 0);
            row.addView(b, UiKit.poids(1));
        }
        LinearLayout.LayoutParams lp = UiKit.pleineLargeur();
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private View carte(Chantier c) {
        Finance f = a.finance(c);
        LinearLayout carte = UiKit.ligne(a, 0);
        carte.setGravity(Gravity.TOP);
        carte.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(20), 0, 0)));
        carte.setClipToOutline(true);
        View vig = Blocs.vignette(a, c, 96, 124, 0, false);
        carte.addView(vig);

        LinearLayout txt = UiKit.colonne(a, 6);
        txt.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout l1 = UiKit.ligne(a, 6);
        TextView t = UiKit.titre(a, c.titre, 19);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l1.addView(t, UiKit.poids(1));
        TextView etiq = etiquette(c, f);
        if (etiq != null) l1.addView(etiq);
        txt.addView(l1, UiKit.pleineLargeur());
        String sous = c.client;
        if (!c.adresse.isEmpty()) sous = sous.isEmpty() ? c.adresse : sous + " · " + c.adresse;
        if (!sous.isEmpty()) {
            TextView s = UiKit.text(a, sous, 14, UiKit.ENCRE_2, false);
            s.setMaxLines(1);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            txt.addView(s);
        }
        long conso = f.consommeBp();
        if (conso >= 0) txt.addView(Blocs.barreBudget(a, conso, c.avancementBp, 7));
        LinearLayout l3 = UiKit.ligne(a, 6);
        String gauche = conso >= 0 ? "Budget " + Finance.pourcent(conso) : "Pas encore de budget";
        if (c.avancementBp >= 0) gauche += " · travaux " + Finance.pourcent(c.avancementBp);
        l3.addView(UiKit.text(a, gauche, 13, UiKit.ENCRE_2, false), UiKit.poids(1));
        long b = f.prevision().benefice;
        if (f.venduHt() > 0) l3.addView(UiKit.text(a, (b > 0 ? "+" : "") + Money.formatCourt(b), 14, b < 0 ? UiKit.ROUGE : UiKit.ENCRE, true));
        txt.addView(l3, UiKit.pleineLargeur());
        carte.addView(txt, UiKit.poids(1));

        carte.setClickable(true);
        carte.setContentDescription("Chantier " + c.titre + ", " + Chantier.libelleStatut(c.statut));
        carte.setOnClickListener(v -> a.ouvrir(new FicheScreen(a, c.id)));
        return carte;
    }

    /** Une seule étiquette, la plus importante : dépassé, à vérifier, sinon « Sans TVA ». */
    private TextView etiquette(Chantier c, Finance f) {
        for (String[] po : Postes.TOUS) if (f.alerte(po[0]) == 2 && !Chantier.TERMINE.equals(c.statut)) return Blocs.etiquette(a, "Dépassé", 2);
        int n = f.aVerifier();
        if (n > 0) return Blocs.etiquette(a, n + " à vérifier", 1);
        if (c.entreProches()) return Blocs.etiquette(a, "Sans TVA", 0);
        return null;
    }
}
