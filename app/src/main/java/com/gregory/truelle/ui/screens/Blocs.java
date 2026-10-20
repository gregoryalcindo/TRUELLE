package com.gregory.truelle.ui.screens;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
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
import com.gregory.truelle.data.Dates;
import com.gregory.truelle.ui.Images;
import com.gregory.truelle.ui.UiKit;

import java.io.File;

/** Briques communes de la nouvelle ergonomie (maquette v3 d'Arthur). */
final class Blocs {

    private Blocs() {}

    /** Carte blanche découpée en rangées séparées par un trait fin. */
    static final class Groupe {
        final LinearLayout vue;
        private final MainActivity a;
        private int n;

        Groupe(MainActivity a) {
            this.a = a;
            vue = UiKit.colonne(a, 0);
            vue.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(a, 20), 0, 0));
            vue.setPadding(0, UiKit.dp(a, 4), 0, UiKit.dp(a, 4));
        }

        void ajouter(View rangee) {
            if (n++ > 0) {
                View trait = new View(a);
                trait.setBackgroundColor(UiKit.SEPARATEUR);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(a, 1));
                lp.leftMargin = UiKit.dp(a, 16);
                vue.addView(trait, lp);
            }
            vue.addView(rangee, UiKit.pleineLargeur());
        }

        boolean vide() {
            return n == 0;
        }
    }

    /** Titre de section avec, à droite, un petit texte ou un lien. */
    static View titreSection(MainActivity a, String titre, String droite, View.OnClickListener l) {
        LinearLayout row = UiKit.ligne(a, 8);
        row.setPadding(0, UiKit.dp(a, 8), 0, 0);
        row.addView(UiKit.titre(a, titre, 22), UiKit.poids(1));
        if (droite != null) {
            TextView t = UiKit.text(a, droite, 15, l != null ? UiKit.ENCRE : UiKit.ENCRE_2, l != null);
            if (l != null) {
                t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                t.setMinHeight(UiKit.dp(a, 40));
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setOnClickListener(l);
            }
            row.addView(t);
        }
        return row;
    }

    /** Pastille d'icône (40 dp) ; fond pierre et icône encre sauf pour un état. */
    static View pastille(MainActivity a, int icone, int fond, int couleur, int tailleDp) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(UiKit.rounded(fond, UiKit.dp(a, tailleDp * 0.3f), 0, 0));
        int ic = Math.round(tailleDp * 0.55f);
        f.addView(UiKit.icone(a, icone, couleur, ic), new FrameLayout.LayoutParams(UiKit.dp(a, ic), UiKit.dp(a, ic), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(a, tailleDp), UiKit.dp(a, tailleDp)));
        return f;
    }

    /** Rangée cliquable : [pastille] titre / sous-titre ……… valeur [›]. */
    static LinearLayout rangee(MainActivity a, View avant, String titre, CharSequence sous, String droite, boolean chevron, View.OnClickListener l) {
        LinearLayout row = UiKit.ligne(a, 14);
        row.setPadding(UiKit.dp(a, 16), UiKit.dp(a, 10), UiKit.dp(a, 14), UiKit.dp(a, 10));
        row.setMinimumHeight(UiKit.dp(a, 60));
        if (avant != null) row.addView(avant);
        LinearLayout textes = UiKit.colonne(a, 2);
        TextView t = UiKit.text(a, titre, 17, UiKit.ENCRE, true);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        textes.addView(t);
        if (sous != null && sous.length() > 0) {
            TextView s = UiKit.text(a, "", 15, UiKit.ENCRE_2, false);
            s.setText(sous);
            textes.addView(s);
        }
        row.addView(textes, UiKit.poids(1));
        if (droite != null) row.addView(UiKit.text(a, droite, 17, UiKit.ENCRE, true));
        if (chevron) row.addView(UiKit.icone(a, R.drawable.ic_chevron, UiKit.ENCRE_2, 20));
        if (l != null) {
            row.setBackground(UiKit.ripple(UiKit.rounded(Color.TRANSPARENT, 0, 0, 0)));
            row.setClickable(true);
            row.setOnClickListener(l);
        }
        row.setContentDescription(titre + (droite != null ? ", " + droite : ""));
        return row;
    }

    /** Case ronde à cocher (48 dp de zone tactile). */
    static View caseTache(MainActivity a, boolean fait, View.OnClickListener l) {
        FrameLayout zone = new FrameLayout(a);
        FrameLayout rond = new FrameLayout(a);
        rond.setBackground(fait ? UiKit.rounded(UiKit.VERT, UiKit.dp(a, 14), 0, 0)
                : UiKit.rounded(UiKit.SURFACE, UiKit.dp(a, 14), UiKit.dp(a, 2), UiKit.ENCRE));
        if (fait) rond.addView(UiKit.icone(a, R.drawable.ic_check, Color.WHITE, 16), new FrameLayout.LayoutParams(UiKit.dp(a, 16), UiKit.dp(a, 16), Gravity.CENTER));
        zone.addView(rond, new FrameLayout.LayoutParams(UiKit.dp(a, 28), UiKit.dp(a, 28), Gravity.CENTER));
        zone.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(a, 44), UiKit.dp(a, 44)));
        zone.setClickable(true);
        zone.setContentDescription(fait ? "Tâche faite, décocher" : "Cocher la tâche");
        zone.setOnClickListener(l);
        return zone;
    }

    /** Rangée de tâche : case · texte · (chantier) · étiquette qui / quand. */
    static LinearLayout rangeeTache(MainActivity a, Chantier.Element t, String chantier, View.OnClickListener cocher, View.OnClickListener ouvrir) {
        boolean fait = Chantier.tacheFaite(t);
        LinearLayout row = UiKit.ligne(a, 8);
        row.setPadding(UiKit.dp(a, 6), UiKit.dp(a, 4), UiKit.dp(a, 14), UiKit.dp(a, 4));
        row.setMinimumHeight(UiKit.dp(a, 56));
        row.addView(caseTache(a, fait, cocher));
        LinearLayout textes = UiKit.colonne(a, 2);
        TextView titre = UiKit.text(a, t.get("texte"), 16, fait ? UiKit.ENCRE_2 : UiKit.ENCRE, !fait);
        if (fait) titre.setPaintFlags(titre.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        textes.addView(titre);
        String sous = chantier == null ? "" : chantier;
        String qui = t.get("qui");
        if (!qui.isEmpty()) sous += (sous.isEmpty() ? "" : " · ") + qui;
        if (!sous.isEmpty()) textes.addView(UiKit.text(a, sous, 14, UiKit.ENCRE_2, false));
        row.addView(textes, UiKit.poids(1));
        if (!fait && !t.get("date").isEmpty()) {
            boolean retard = Dates.passee(t.get("date"));
            TextView q = UiKit.chip(a, retard ? "En retard" : Dates.relatif(t.get("date")),
                    retard ? UiKit.ROUGE_FOND : UiKit.FOND, retard ? UiKit.ROUGE : UiKit.ENCRE);
            q.setTextSize(13);
            row.addView(q);
        }
        if (ouvrir != null) {
            row.setBackground(UiKit.ripple(UiKit.rounded(Color.TRANSPARENT, 0, 0, 0)));
            row.setClickable(true);
            row.setOnClickListener(ouvrir);
        }
        row.setContentDescription(t.get("texte") + (fait ? ", faite" : ""));
        return row;
    }

    /**
     * Barre « budget utilisé » avec le repère « travaux faits ».
     * Encre si tout va bien, ambre à partir de 80 %, rouge au-delà du budget.
     */
    static View barreBudget(MainActivity a, long consommeBp, int avancementBp, int hauteurDp) {
        FrameLayout fond = new FrameLayout(a);
        fond.setBackground(UiKit.rounded(UiKit.SEPARATEUR, UiKit.dp(a, hauteurDp / 2f), 0, 0));
        View plein = new View(a);
        int couleur = consommeBp > 10000 ? UiKit.ROUGE : consommeBp >= 8000 ? UiKit.AMBRE_VIF : UiKit.ENCRE;
        plein.setBackground(UiKit.rounded(couleur, UiKit.dp(a, hauteurDp / 2f), 0, 0));
        fond.addView(plein, new FrameLayout.LayoutParams(0, UiKit.dp(a, hauteurDp)));
        View repere = new View(a);
        repere.setBackground(UiKit.rounded(Color.parseColor("#8A8478"), UiKit.dp(a, 2), UiKit.dp(a, 1), Color.WHITE));
        if (avancementBp >= 0) fond.addView(repere, new FrameLayout.LayoutParams(UiKit.dp(a, 4), UiKit.dp(a, hauteurDp)));
        float ratio = Math.max(0f, Math.min(1f, consommeBp / 10000f));
        float av = Math.max(0f, Math.min(1f, avancementBp / 10000f));
        fond.post(() -> {
            int w = fond.getWidth();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) plein.getLayoutParams();
            lp.width = Math.round(w * ratio);
            plein.setLayoutParams(lp);
            if (avancementBp >= 0) {
                FrameLayout.LayoutParams lr = (FrameLayout.LayoutParams) repere.getLayoutParams();
                lr.leftMargin = Math.max(0, Math.round(w * av) - UiKit.dp(a, 2));
                repere.setLayoutParams(lr);
            }
        });
        fond.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(a, hauteurDp));
        lp.topMargin = UiKit.dp(a, 4);
        lp.bottomMargin = UiKit.dp(a, 4);
        fond.setLayoutParams(lp);
        return fond;
    }

    /** Étiquette d'état. niveau : 0 neutre, 1 attention (ambre), 2 problème (rouge), 3 acquis (vert), 4 en cours (bleu). */
    static TextView etiquette(MainActivity a, String texte, int niveau) {
        int fond, coul;
        switch (niveau) {
            case 1: fond = UiKit.AMBRE_FOND; coul = UiKit.AMBRE; break;
            case 2: fond = UiKit.ROUGE; coul = Color.WHITE; break;
            case 3: fond = UiKit.VERT_FOND; coul = UiKit.VERT; break;
            case 4: fond = UiKit.BLEU_FOND; coul = UiKit.BLEU; break;
            default: fond = UiKit.PIERRE; coul = UiKit.ENCRE;
        }
        TextView t = UiKit.chip(a, texte, fond, coul);
        t.setTextSize(13);
        t.setPadding(UiKit.dp(a, 9), UiKit.dp(a, 3), UiKit.dp(a, 9), UiKit.dp(a, 3));
        return t;
    }

    /**
     * Vignette d'un chantier : sa photo de couverture, sinon un aplat pierre avec ses initiales.
     * Les miniatures sont petites : lues directement.
     */
    static View vignette(MainActivity a, Chantier c, int largeurDp, int hauteurDp, float rayonDp, boolean coinsHautSeulement) {
        FrameLayout f = new FrameLayout(a);
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(UiKit.PIERRE);
        float r = UiKit.dp(a, rayonDp);
        fond.setCornerRadii(coinsHautSeulement ? new float[]{r, r, r, r, 0, 0, 0, 0} : new float[]{r, r, r, r, r, r, r, r});
        f.setBackground(fond);
        f.setClipToOutline(true);
        Chantier.Element p = c.photoCouverture();
        Bitmap bmp = null;
        if (p != null) {
            File mini = a.store().fichier(c.id, p.get("miniature"));
            if (mini == null) mini = a.store().fichier(c.id, p.get("fichier"));
            bmp = Images.charger(mini, UiKit.dp(a, Math.min(largeurDp, 400)));
        }
        if (bmp != null) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            iv.setContentDescription("Photo du chantier " + c.titre);
            f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView t = UiKit.titre(a, initiales(c.titre), Math.min(28, hauteurDp / 3f));
            t.setTextColor(UiKit.ENCRE_2);
            f.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        f.setLayoutParams(new LinearLayout.LayoutParams(largeurDp < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : UiKit.dp(a, largeurDp), UiKit.dp(a, hauteurDp)));
        return f;
    }

    /** Coche ou décoche une tâche (une correction dans le journal, rien n'est écrasé). */
    static void basculerTache(MainActivity a, String chantierId, Chantier.Element t, Runnable apres) {
        boolean fait = !Chantier.tacheFaite(t);
        java.util.Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("cible", t.id);
        data.put("fait", fait ? "oui" : "non");
        data.put("fait_le", fait ? Dates.aujourdhui() : "");
        data.put("fait_par", fait ? a.auteur() : "");
        try {
            a.store().ajouter(chantierId, com.gregory.truelle.data.Event.create(com.gregory.truelle.data.Event.CORRECTION, chantierId, a.auteur(), data));
            a.apresEnregistrement();
            if (fait) a.confirmer("Tâche faite ✓");
        } catch (Exception e) {
            android.widget.Toast.makeText(a, "Enregistrement impossible : " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
        apres.run();
    }

    static String initiales(String titre) {
        StringBuilder b = new StringBuilder();
        for (String m : titre.trim().split("[^\\p{L}\\p{N}]+")) {
            if (m.isEmpty() || m.length() < 3 && b.length() > 0) continue;
            b.append(Character.toUpperCase(m.charAt(0)));
            if (b.length() == 2) break;
        }
        return b.length() == 0 ? "?" : b.toString();
    }

    /** Pastille ronde d'une personne (initiale) : encre pour « moi », gris chaud pour l'autre téléphone. */
    static View avatar(MainActivity a, String nom) {
        boolean moi = nom.equalsIgnoreCase(a.auteur());
        TextView t = UiKit.text(a, nom.isEmpty() ? "?" : nom.substring(0, 1).toUpperCase(), 13, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(UiKit.rounded(moi ? UiKit.ENCRE : Color.parseColor("#6B665C"), UiKit.dp(a, 16), 0, 0));
        t.setLayoutParams(new LinearLayout.LayoutParams(UiKit.dp(a, 32), UiKit.dp(a, 32)));
        return t;
    }
}
