package com.gregory.truelle.ui.screens;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.Locale;

/** Calculs rapides du métier : le résultat se met à jour pendant la saisie. */
public final class CalculScreen extends Screen {

    public static final String CARRELAGE = "carrelage";
    public static final String PEINTURE = "peinture";
    public static final String BETON = "beton";
    public static final String TVA = "tva";

    private final String mode;
    private int tvaBp = Tva.NORMAL;
    private boolean depuisTtc = true;
    private String montant = "";

    public CalculScreen(MainActivity a, String mode) {
        super(a);
        this.mode = mode;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Outils", v -> a.retour()));
        switch (mode) {
            case CARRELAGE: carrelage(p); break;
            case PEINTURE: peinture(p); break;
            case BETON: beton(p); break;
            default: tva(p);
        }
        return p.racine;
    }

    // ------------------------------------------------------------- carrelage

    private void carrelage(Page p) {
        p.entete.addView(UiKit.titre(a, "Carrelage", 30));
        EditText lon = nombre("Ex. : 3,2"), lar = nombre("Ex. : 2,5"), surf = nombre("Ou la surface directement"),
                perte = nombre("10"), carton = nombre("Ex. : 1,44");
        perte.setText("10");
        ligne2(p, "Longueur (m)", lon, "Largeur (m)", lar);
        p.contenu.addView(UiKit.libelle(a, "Surface (m²)", surf), UiKit.pleineLargeur());
        ligne2(p, "Perte et coupes (%)", perte, "m² par carton", carton);
        p.contenu.addView(UiKit.text(a, "Perte : 10 % en pose droite, 15 % en diagonale ou avec beaucoup de découpes.", 14, UiKit.ENCRE_2, false));
        TextView res = resultat(p);
        Runnable calc = () -> {
            double s = val(surf) > 0 ? val(surf) : val(lon) * val(lar);
            double avecPerte = s * (1 + val(perte) / 100);
            if (s <= 0) {
                res.setText("Indiquez les dimensions ou la surface.");
                return;
            }
            String t = "Surface : " + f2(s) + " m²\nÀ commander : " + f2(avecPerte) + " m²";
            if (val(carton) > 0) t += "\nSoit " + (int) Math.ceil(avecPerte / val(carton)) + " cartons";
            res.setText(t);
        };
        ecouter(calc, lon, lar, surf, perte, carton);
        calc.run();
    }

    // ------------------------------------------------------------- peinture

    private void peinture(Page p) {
        p.entete.addView(UiKit.titre(a, "Peinture", 30));
        EditText per = nombre("Ex. : 14"), haut = nombre("Ex. : 2,5"), ouv = nombre("Ex. : 3"), surf = nombre("Ou la surface directement"),
                couches = nombre("2"), rend = nombre("10");
        couches.setText("2");
        rend.setText("10");
        ligne2(p, "Tour de la pièce (m)", per, "Hauteur (m)", haut);
        ligne2(p, "Portes, fenêtres (m²)", ouv, "Surface (m²)", surf);
        ligne2(p, "Couches", couches, "Rendement (m²/L)", rend);
        p.contenu.addView(UiKit.text(a, "Le rendement est écrit sur le pot (souvent 10 à 12 m² par litre et par couche).", 14, UiKit.ENCRE_2, false));
        TextView res = resultat(p);
        Runnable calc = () -> {
            double s = val(surf) > 0 ? val(surf) : Math.max(0, val(per) * val(haut) - val(ouv));
            if (s <= 0 || val(rend) <= 0) {
                res.setText("Indiquez les dimensions ou la surface.");
                return;
            }
            double litres = s * Math.max(1, val(couches)) / val(rend);
            res.setText("Surface à peindre : " + f2(s) + " m²\nPeinture : " + f1(litres) + " L (" + (int) Math.ceil(litres) + " L en arrondissant)");
        };
        ecouter(calc, per, haut, ouv, surf, couches, rend);
        calc.run();
    }

    // ------------------------------------------------------------- béton / chape

    private void beton(Page p) {
        p.entete.addView(UiKit.titre(a, "Béton · chape", 30));
        EditText lon = nombre("Ex. : 4"), lar = nombre("Ex. : 3"), surf = nombre("Ou la surface directement"), ep = nombre("Ex. : 5"), perte = nombre("5");
        perte.setText("5");
        ligne2(p, "Longueur (m)", lon, "Largeur (m)", lar);
        p.contenu.addView(UiKit.libelle(a, "Surface (m²)", surf), UiKit.pleineLargeur());
        ligne2(p, "Épaisseur (cm)", ep, "Marge (%)", perte);
        TextView res = resultat(p);
        Runnable calc = () -> {
            double s = val(surf) > 0 ? val(surf) : val(lon) * val(lar);
            if (s <= 0 || val(ep) <= 0) {
                res.setText("Indiquez la surface et l'épaisseur.");
                return;
            }
            double m3 = s * val(ep) / 100 * (1 + val(perte) / 100);
            res.setText("Surface : " + f2(s) + " m²\nVolume : " + f2(m3) + " m³ (" + (int) Math.ceil(m3 * 1000) + " litres)");
        };
        ecouter(calc, lon, lar, surf, ep, perte);
        calc.run();
    }

    // ------------------------------------------------------------- HT ↔ TTC

    private void tva(Page p) {
        p.entete.addView(UiKit.titre(a, "HT ↔ TTC", 30));
        p.contenu.addView(UiKit.choix(a, new String[][]{{"ttc", "J'ai le TTC"}, {"ht", "J'ai le HT"}}, depuisTtc ? "ttc" : "ht", choisi -> {
            depuisTtc = "ttc".equals(choisi);
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        EditText m = UiKit.champMontant(a, montant);
        p.contenu.addView(UiKit.libelle(a, depuisTtc ? "Montant TTC" : "Montant HT", UiKit.euro(a, m)), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.blocTva(a, "Taux de TVA", tvaBp, "", t -> {
            montant = m.getText().toString();
            tvaBp = t;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        TextView res = resultat(p);
        Runnable calc = () -> {
            montant = m.getText().toString();
            long c = Money.parseCentimes(montant.trim());
            if (c <= 0) {
                res.setText("Indiquez un montant.");
                return;
            }
            long ht = depuisTtc ? Tva.ht(c, tvaBp) : c;
            long ttc = depuisTtc ? c : Tva.ttc(c, tvaBp);
            res.setText("HT : " + Money.format(ht) + "\nTVA " + Tva.libelle(tvaBp) + " : " + Money.format(ttc - ht) + "\nTTC : " + Money.format(ttc));
        };
        ecouter(calc, m);
        calc.run();
    }

    // ------------------------------------------------------------- outils

    private EditText nombre(String hint) {
        EditText e = UiKit.champ(a, "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        e.setHint(hint);
        return e;
    }

    private void ligne2(Page p, String l1, EditText e1, String l2, EditText e2) {
        LinearLayout row = UiKit.ligne(a, 10);
        row.setGravity(android.view.Gravity.TOP);
        row.addView(UiKit.libelle(a, l1, e1), UiKit.poids(1));
        row.addView(UiKit.libelle(a, l2, e2), UiKit.poids(1));
        p.contenu.addView(row, UiKit.pleineLargeur());
    }

    private TextView resultat(Page p) {
        TextView t = UiKit.titre(a, "", 22);
        t.setPadding(dp(18), dp(16), dp(18), dp(16));
        t.setBackground(UiKit.rounded(UiKit.PIERRE, dp(18), 0, 0));
        t.setLineSpacing(dp(4), 1f);
        p.contenu.addView(t, UiKit.pleineLargeur());
        return t;
    }

    private static void ecouter(Runnable r, EditText... champs) {
        for (EditText e : champs) {
            e.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    r.run();
                }
            });
        }
    }

    private static double val(EditText e) {
        try {
            String s = e.getText().toString().trim().replace(',', '.');
            return s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String f2(double d) {
        return String.format(Locale.FRANCE, "%.2f", d);
    }

    private static String f1(double d) {
        return String.format(Locale.FRANCE, "%.1f", d);
    }
}
