package com.gregory.truelle.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.data.Chantier;

/**
 * Composants visuels partagés. Palette validée par Arthur :
 * orange = actions uniquement, bleu = en cours, vert = terminé / OK,
 * gris = à démarrer et neutre, noir = texte, chiffres et sélection, rouge = alertes.
 */
public final class UiKit {

    private UiKit() {}

    public static final int FOND = Color.parseColor("#F2F1EC");
    public static final int SURFACE = Color.WHITE;
    public static final int ENCRE = Color.parseColor("#1B1F23");
    public static final int ENCRE_2 = Color.parseColor("#4A4F55");
    public static final int BORDURE = Color.parseColor("#C9C6BD");
    public static final int NEUTRE = Color.parseColor("#E8E6DF");
    public static final int ACTION = Color.parseColor("#C2410C");
    public static final int BLEU = Color.parseColor("#1E3A8A");
    public static final int BLEU_FOND = Color.parseColor("#DCE6F5");
    public static final int VERT = Color.parseColor("#14532D");
    public static final int VERT_FOND = Color.parseColor("#D9EEDD");
    public static final int GRIS = Color.parseColor("#3F444A");
    public static final int GRIS_FOND = Color.parseColor("#E2E0D9");
    public static final int ROUGE = Color.parseColor("#B91C1C");
    /** Voile posé sous les panneaux (#991B1F23) et le même voile déjà mélangé au fond, pour les bords d'écran. */
    public static final int VOILE = Color.parseColor("#991B1F23");
    public static final int FOND_VOILE = Color.parseColor("#717373");

    public static final Typeface TITRE = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    // ------------------------------------------------------------- textes

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView titre(Context c, String s, float sp) {
        TextView t = text(c, s, sp, ENCRE, false);
        t.setTypeface(TITRE);
        t.setLineSpacing(0, 1.05f);
        return t;
    }

    /** Petite étiquette colorée (statut, mode…). */
    public static TextView chip(Context c, String s, int fond, int texte) {
        TextView t = text(c, s, 16, texte, true);
        t.setPadding(dp(c, 12), dp(c, 5), dp(c, 12), dp(c, 5));
        t.setBackground(rounded(fond, dp(c, 8), 0, 0));
        return t;
    }

    public static TextView chipContour(Context c, String s) {
        TextView t = text(c, s, 15, ENCRE, true);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(rounded(Color.TRANSPARENT, dp(c, 8), dp(c, 2), ENCRE));
        return t;
    }

    /** {fond, texte} selon le statut, toujours accompagné du mot. */
    public static int[] couleursStatut(String statut) {
        if (Chantier.EN_COURS.equals(statut)) return new int[]{BLEU_FOND, BLEU};
        if (Chantier.TERMINE.equals(statut)) return new int[]{VERT_FOND, VERT};
        return new int[]{GRIS_FOND, GRIS};
    }

    // ------------------------------------------------------------- boutons

    /** Bouton plein (orange pour l'action principale). Hauteur minimale 56 dp pour les doigts et les gants. */
    public static Button bouton(Context c, String label, int fond, int texte, int hauteurDp, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(texte);
        b.setTypeface(TITRE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, hauteurDp >= 64 ? 22 : 19);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, hauteurDp));
        b.setMinimumHeight(dp(c, hauteurDp));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setBackground(ripple(rounded(fond, dp(c, 16), 0, 0)));
        b.setOnClickListener(l);
        return b;
    }

    public static Button boutonAction(Context c, String label, View.OnClickListener l) {
        return bouton(c, label, ACTION, Color.WHITE, 64, l);
    }

    /** Bouton secondaire : contour noir, fond transparent. */
    public static Button boutonContour(Context c, String label, View.OnClickListener l) {
        Button b = bouton(c, label, Color.TRANSPARENT, ENCRE, 56, l);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setBackground(ripple(rounded(Color.TRANSPARENT, dp(c, 14), dp(c, 2), ENCRE)));
        return b;
    }

    /** Bouton de choix (statut, mode). Sélectionné : couleurs données + bordure épaisse. */
    public static Button boutonChoix(Context c, String label, boolean selectionne, int fondSel, int texteSel, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setMinHeight(dp(c, 56));
        b.setMinimumHeight(dp(c, 56));
        b.setPadding(dp(c, 6), 0, dp(c, 6), 0);
        if (selectionne) {
            b.setTextColor(texteSel);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setBackground(ripple(rounded(fondSel, dp(c, 12), dp(c, 3), texteSel == Color.WHITE ? fondSel : texteSel)));
        } else {
            b.setTextColor(ENCRE);
            b.setBackground(ripple(rounded(SURFACE, dp(c, 12), dp(c, 2), BORDURE)));
        }
        b.setOnClickListener(l);
        return b;
    }

    /** Lien de retour en haut d'écran (« ‹ Mes chantiers »). */
    public static View retour(Context c, String label, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 48));
        row.addView(icone(c, com.gregory.truelle.R.drawable.ic_back, ENCRE, 26));
        TextView t = text(c, label, 18, ENCRE, true);
        t.setPadding(dp(c, 6), 0, dp(c, 12), 0);
        row.addView(t);
        row.setBackground(ripple(rounded(Color.TRANSPARENT, dp(c, 8), 0, 0)));
        row.setClickable(true);
        row.setContentDescription("Retour : " + label);
        row.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(lp);
        return row;
    }

    public static ImageView icone(Context c, int res, int color, int tailleDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, tailleDp), dp(c, tailleDp)));
        return iv;
    }

    /** Pastille carrée avec une icône, pour les lignes de liste. */
    public static FrameLayout pastille(Context c, int res) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(rounded(NEUTRE, dp(c, 12), 0, 0));
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(ENCRE));
        f.addView(iv, new FrameLayout.LayoutParams(dp(c, 26), dp(c, 26), Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48));
        f.setLayoutParams(lp);
        return f;
    }

    // ------------------------------------------------------------- champs

    public static EditText champ(Context c, String valeur, int inputType) {
        EditText e = new EditText(c);
        e.setText(valeur);
        e.setInputType(inputType);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        e.setTextColor(ENCRE);
        e.setHintTextColor(ENCRE_2);
        e.setMinHeight(dp(c, 56));
        e.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        e.setBackground(rounded(SURFACE, dp(c, 12), dp(c, 2), BORDURE));
        return e;
    }

    public static EditText champTexte(Context c, String valeur) {
        return champ(c, valeur, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    /** Un libellé au-dessus d'un champ, comme sur les maquettes. */
    public static LinearLayout libelle(Context c, String label, View champ) {
        LinearLayout col = colonne(c, 6);
        TextView l = text(c, label, 16, ENCRE_2, false);
        l.setLabelFor(champ.getId() == View.NO_ID ? assignerId(champ) : champ.getId());
        col.addView(l);
        col.addView(champ, pleineLargeur());
        return col;
    }

    private static int assignerId(View v) {
        int id = View.generateViewId();
        v.setId(id);
        return id;
    }

    // ------------------------------------------------------------- mises en page

    public static LinearLayout colonne(Context c, int espaceDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        if (espaceDp > 0) l.setDividerDrawable(espace(c, espaceDp));
        l.setShowDividers(espaceDp > 0 ? LinearLayout.SHOW_DIVIDER_MIDDLE : LinearLayout.SHOW_DIVIDER_NONE);
        return l;
    }

    public static LinearLayout ligne(Context c, int espaceDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        if (espaceDp > 0) {
            l.setDividerDrawable(espace(c, espaceDp));
            l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        }
        return l;
    }

    /** Carte blanche arrondie. */
    public static LinearLayout carte(Context c, int espaceDp) {
        LinearLayout l = colonne(c, espaceDp);
        l.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        l.setBackground(ripple(rounded(SURFACE, dp(c, 16), 0, 0)));
        return l;
    }

    public static LinearLayout.LayoutParams pleineLargeur() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams poids(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams remplirHauteur() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    private static Drawable espace(Context c, int dpSize) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setSize(dp(c, dpSize), dp(c, dpSize));
        return g;
    }

    public static GradientDrawable rounded(int fond, int rayon, int bordure, int couleurBordure) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fond);
        g.setCornerRadius(rayon);
        if (bordure > 0) g.setStroke(bordure, couleurBordure);
        return g;
    }

    public static RippleDrawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33000000")), content, null);
    }
}
