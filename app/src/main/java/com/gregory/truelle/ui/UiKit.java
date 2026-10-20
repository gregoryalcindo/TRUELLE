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
 * Composants visuels partagés. Règle d'Arthur : « la couleur est un signal, pas une décoration ».
 * Base neutre (crème, blanc, pierre, gris, encre) pour la structure, les textes, les montants
 * et les boutons. Couleur seulement pour un état, toujours avec un mot ou une icône :
 * vert = acquis, ambre = attention, rouge = problème, bleu = en cours.
 */
public final class UiKit {

    private UiKit() {}

    public static final int FOND = Color.parseColor("#F2F1EC");
    public static final int SURFACE = Color.WHITE;
    public static final int ENCRE = Color.parseColor("#1B1F23");
    public static final int ENCRE_2 = Color.parseColor("#4A4F55");
    public static final int BORDURE = Color.parseColor("#C9C6BD");
    public static final int NEUTRE = Color.parseColor("#E8E6DF");
    /** Action principale : encre (plus de terracotta dans l'interface). */
    public static final int ACTION = Color.parseColor("#1B1F23");
    /** Carte mise en avant (« À encaisser »). */
    public static final int PIERRE = Color.parseColor("#E6E3DA");
    public static final int AMBRE = Color.parseColor("#92400E");
    public static final int AMBRE_FOND = Color.parseColor("#FDEFD3");
    public static final int AMBRE_VIF = Color.parseColor("#B45309");
    public static final int ROUGE_FOND = Color.parseColor("#FBE3E1");
    public static final int SEPARATEUR = Color.parseColor("#ECE9E1");
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

    /** Repères posés sur les vues : la zone qui défile (position gardée), le panneau du bouton +. */
    public static final String TAG_DEFILE = "defile";
    public static final String TAG_PANNEAU = "panneau";

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

    /** Champ de montant : gros chiffres, clavier numérique, virgule ou point acceptés. */
    public static EditText champMontant(Context c, String valeur) {
        EditText e = champ(c, valeur, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTypeface(TITRE);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        e.setHint("0,00 €");
        e.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        e.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    /** Ajoute le signe € à droite, à l'intérieur d'un champ de montant. */
    public static View euro(Context c, EditText champ) {
        FrameLayout cadre = new FrameLayout(c);
        champ.setPadding(champ.getPaddingLeft(), champ.getPaddingTop(), dp(c, 48), champ.getPaddingBottom());
        cadre.addView(champ, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView signe = text(c, "€", champ.getTextSize() / c.getResources().getDisplayMetrics().scaledDensity, ENCRE_2, false);
        signe.setTypeface(champ.getTypeface());
        signe.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        lp.setMarginEnd(dp(c, 16));
        cadre.addView(signe, lp);
        champ.setContentDescription("Montant en euros");
        return cadre;
    }

    /**
     * Choix du taux de TVA : 0 · 5,5 · 10 · 20 % et « Autre » (saisie libre de 0 à 100 %).
     * Le taux actuel, s'il n'est pas courant, s'affiche sur le bouton « Autre ».
     */
    public static LinearLayout choixTva(Context c, int bp, java.util.function.IntConsumer onChoix) {
        LinearLayout row = ligne(c, 6);
        boolean courant = false;
        for (int t : com.gregory.truelle.data.Tva.COURANTS) {
            boolean sel = t == bp;
            courant |= sel;
            Button b = boutonChoix(c, com.gregory.truelle.data.Tva.libelle(t).replace(" %", "%"), sel, ENCRE, Color.WHITE, v -> onChoix.accept(t));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.addView(b, poids(1));
        }
        boolean autre = !courant && bp >= 0;
        Button b = boutonChoix(c, autre ? com.gregory.truelle.data.Tva.libelle(bp).replace(" %", "%") : "Autre", autre, ENCRE, Color.WHITE, v -> {
            EditText champ = champ(c, autre ? com.gregory.truelle.data.Tva.libelle(bp).replace(" %", "") : "",
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            champ.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
            champ.setHint("Ex. : 2,1");
            FrameLayout cadre = new FrameLayout(c);
            cadre.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0);
            cadre.addView(champ);
            new android.app.AlertDialog.Builder(c)
                    .setTitle("Taux de TVA (%)")
                    .setView(cadre)
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("OK", (d, w) -> {
                        int t = com.gregory.truelle.data.Tva.parse(champ.getText().toString());
                        if (t >= 0) onChoix.accept(t);
                        else android.widget.Toast.makeText(c, "Taux invalide (0 à 100 %)", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(b, poids(1.2f));
        return row;
    }

    /** Bloc « libellé + choix TVA + explication » prêt à poser dans un formulaire. */
    public static LinearLayout blocTva(Context c, String titre, int bp, String aide, java.util.function.IntConsumer onChoix) {
        LinearLayout bloc = colonne(c, 8);
        bloc.addView(text(c, titre, 16, ENCRE_2, false));
        bloc.addView(choixTva(c, bp, onChoix), pleineLargeur());
        if (aide != null && !aide.isEmpty()) bloc.addView(text(c, aide, 15, ENCRE_2, false));
        // « Quel taux ? » : le guide de Karim, qui peut appliquer directement le taux trouvé.
        if (c instanceof com.gregory.truelle.MainActivity) {
            com.gregory.truelle.MainActivity a = (com.gregory.truelle.MainActivity) c;
            TextView guide = text(c, "Quel taux choisir ? Ouvrir le guide TVA ›", 16, ENCRE, true);
            guide.setPaintFlags(guide.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            guide.setMinHeight(dp(c, 44));
            guide.setGravity(Gravity.CENTER_VERTICAL);
            guide.setOnClickListener(v -> a.ouvrir(new com.gregory.truelle.ui.screens.TvaGuideScreen(a, onChoix)));
            bloc.addView(guide);
        }
        return bloc;
    }

    /** Une rangée de boutons de choix exclusifs ({valeur, libellé}). La sélection est en noir. */
    public static LinearLayout choix(Context c, String[][] options, String selection, java.util.function.Consumer<String> onChoix) {
        LinearLayout row = ligne(c, 8);
        for (String[] o : options) {
            row.addView(boutonChoix(c, o[1], o[0].equals(selection), ENCRE, Color.WHITE, v -> onChoix.accept(o[0])), poids(1));
        }
        return row;
    }

    /** Ligne de liste cliquable : titre, sous-titre, valeur à droite, étiquette de statut. */
    public static LinearLayout ligneListe(Context c, String titre, String sousTitre, String droite, TextView chip, View.OnClickListener l) {
        LinearLayout row = ligne(c, 12);
        row.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        row.setBackground(ripple(rounded(SURFACE, dp(c, 14), 0, 0)));
        LinearLayout textes = colonne(c, 4);
        TextView t = text(c, titre, 18, ENCRE, true);
        t.setMaxLines(2);
        textes.addView(t);
        if (sousTitre != null && !sousTitre.isEmpty()) textes.addView(text(c, sousTitre, 16, ENCRE_2, false));
        if (chip != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textes.addView(chip, lp);
        }
        row.addView(textes, poids(1));
        if (droite != null) row.addView(text(c, droite, 18, ENCRE, true));
        if (l != null) {
            row.setClickable(true);
            row.setOnClickListener(l);
        }
        row.setContentDescription(titre + (droite != null ? ", " + droite : ""));
        return row;
    }

    /** Ligne « libellé ……… valeur » pour les récapitulatifs chiffrés. */
    public static LinearLayout ligneChiffre(Context c, String libelle, String valeur, boolean gras) {
        LinearLayout row = ligne(c, 8);
        row.addView(text(c, libelle, 17, gras ? ENCRE : ENCRE_2, gras), poids(1));
        row.addView(text(c, valeur, gras ? 19 : 17, ENCRE, true));
        return row;
    }

    /** Titre de section dans un contenu. */
    public static TextView section(Context c, String s) {
        TextView t = titre(c, s, 20);
        t.setPadding(0, dp(c, 6), 0, 0);
        return t;
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
