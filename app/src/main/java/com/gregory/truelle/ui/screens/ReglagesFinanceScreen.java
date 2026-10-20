package com.gregory.truelle.ui.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Reglages;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Réglages du suivi financier de l'entreprise (chantiers « Professionnel ») :
 * TVA facturée ou franchise, TVA des achats récupérée ou non, coût horaire,
 * frais généraux, marge visée, TVA par défaut. Partagés entre les deux téléphones.
 */
public final class ReglagesFinanceScreen extends Screen {

    private Reglages r;
    private String taux;
    private boolean valideDOffice;

    public ReglagesFinanceScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        if (r == null) {
            r = a.reglages();
            taux = r.tauxHoraireCentimes > 0 ? Money.toInput(r.tauxHoraireCentimes) : "";
            valideDOffice = a.valideDOffice();
        }
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Réglages", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Suivi financier", 30));
        p.entete.addView(UiKit.text(a, "S'applique à tous les chantiers. Entre proches, seule la TVA est ignorée.", 15, UiKit.ENCRE_2, false));

        EditText fTaux = UiKit.champMontant(a, taux);
        fTaux.setHint("Ex. : 35");
        Runnable garder = () -> taux = fTaux.getText().toString().trim();

        // TVA facturée aux clients
        LinearLayout ventes = carte();
        ventes.addView(UiKit.titre(a, "TVA sur vos factures", 20));
        ventes.addView(UiKit.choix(a, new String[][]{{Reglages.ASSUJETTI, "Je facture la TVA"}, {Reglages.FRANCHISE, "Franchise (pas de TVA)"}}, r.regimeTva, v -> {
            garder.run();
            r.regimeTva = v;
            if (r.franchise()) r.recupereTva = false;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        ventes.addView(UiKit.text(a, r.franchise()
                ? "Micro-entreprise sous le seuil : les montants sont nets, le PDF porte « TVA non applicable, art. 293 B du CGI »."
                : "Chaque devis et chaque ligne peut avoir son taux : 0 %, 5,5 %, 10 %, 20 % ou autre.", 15, UiKit.ENCRE_2, false));
        if (!r.franchise()) {
            ventes.addView(UiKit.blocTva(a, "TVA proposée par défaut", r.tvaDefautBp,
                    "Modifiable chantier par chantier et ligne par ligne.", t -> {
                        garder.run();
                        r.tvaDefautBp = t;
                        a.remplacer(this);
                    }), UiKit.pleineLargeur());
        }
        p.contenu.addView(ventes, UiKit.pleineLargeur());

        // TVA des achats
        if (!r.franchise()) {
            LinearLayout achats = carte();
            achats.addView(UiKit.titre(a, "TVA de vos achats", 20));
            achats.addView(UiKit.choix(a, new String[][]{{"non", "Coût = prix payé TTC"}, {"oui", "Je récupère la TVA"}}, r.recupereTva ? "oui" : "non", v -> {
                garder.run();
                r.recupereTva = "oui".equals(v);
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            achats.addView(UiKit.text(a, r.recupereTva
                    ? "Les coûts sont comptés hors TVA : la TVA payée est déduite de celle que vous reversez."
                    : "Les achats comptent pour ce que vous avez payé, TVA comprise. Recommandé si vous ne déduisez pas la TVA.", 15, UiKit.ENCRE_2, false));
            p.contenu.addView(achats, UiKit.pleineLargeur());
        }

        // Coûts et prix
        LinearLayout couts = carte();
        couts.addView(UiKit.titre(a, "Coûts et prix", 20));
        couts.addView(UiKit.libelle(a, "Coût d'une heure de travail, charges comprises (€/h)", UiKit.euro(a, fTaux)), UiKit.pleineLargeur());
        couts.addView(UiKit.text(a, "Frais généraux (assurance, véhicule, outillage, compta…) en % des coûts du chantier", 16, UiKit.ENCRE_2, false));
        couts.addView(plusMoins(Tva.libelle(r.fraisGenerauxBp), () -> {
            garder.run();
            r.fraisGenerauxBp = Math.max(0, r.fraisGenerauxBp - 100);
            a.remplacer(this);
        }, () -> {
            garder.run();
            r.fraisGenerauxBp = Math.min(10000, r.fraisGenerauxBp + 100);
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        couts.addView(UiKit.text(a, "Marge visée, pour le « prix conseillé » des lignes de devis", 16, UiKit.ENCRE_2, false));
        couts.addView(plusMoins(Tva.libelle(r.margeViseeBp), () -> {
            garder.run();
            r.margeViseeBp = Math.max(0, r.margeViseeBp - 100);
            a.remplacer(this);
        }, () -> {
            garder.run();
            r.margeViseeBp = Math.min(10000, r.margeViseeBp + 100);
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        p.contenu.addView(couts, UiKit.pleineLargeur());

        // Validation des dépenses (réglage de ce téléphone)
        LinearLayout valid = carte();
        valid.addView(UiKit.titre(a, "Dépenses saisies sur ce téléphone", 20));
        valid.addView(UiKit.choix(a, new String[][]{{"non", "À vérifier"}, {"oui", "Validées d'office"}}, valideDOffice ? "oui" : "non", v -> {
            garder.run();
            valideDOffice = "oui".equals(v);
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        valid.addView(UiKit.text(a, "« À vérifier » : la personne qui gère contrôle montant et TVA (fiche du chantier → Dépenses), puis valide.", 15, UiKit.ENCRE_2, false));
        p.contenu.addView(valid, UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            long t = taux.isEmpty() ? 0 : Money.parseCentimes(taux);
            if (t < 0) {
                fTaux.setError("Montant invalide");
                return;
            }
            Reglages avant = a.reglages();
            Map<String, String> data = new LinkedHashMap<>();
            if (!r.regimeTva.equals(avant.regimeTva)) data.put("regime_tva", r.regimeTva);
            if (r.recupereTva != avant.recupereTva) data.put("recupere_tva", r.recupereTva ? "oui" : "non");
            if (t != avant.tauxHoraireCentimes) data.put("taux_horaire_centimes", Long.toString(t));
            if (r.fraisGenerauxBp != avant.fraisGenerauxBp) data.put("frais_generaux_bp", Integer.toString(r.fraisGenerauxBp));
            if (r.margeViseeBp != avant.margeViseeBp) data.put("marge_visee_bp", Integer.toString(r.margeViseeBp));
            if (r.tvaDefautBp != avant.tvaDefautBp) data.put("tva_defaut_bp", Integer.toString(r.tvaDefautBp));
            if (!data.isEmpty()) {
                try {
                    store().ajouterApp(Event.create(Event.ENTREPRISE, null, a.auteur(), data));
                    a.apresEnregistrement();
                } catch (Exception ex) {
                    toast("Enregistrement impossible : " + ex.getMessage());
                    return;
                }
            }
            a.setValideDOffice(valideDOffice);
            toast("Réglages enregistrés ✓");
            a.retour();
        }));
        return p.racine;
    }

    private View plusMoins(String valeur, Runnable moins, Runnable plus) {
        LinearLayout pm = UiKit.ligne(a, 10);
        pm.addView(UiKit.boutonContour(a, "−", v -> moins.run()), new LinearLayout.LayoutParams(dp(64), dp(56)));
        TextView val = UiKit.titre(a, valeur, 24);
        val.setGravity(Gravity.CENTER);
        pm.addView(val, UiKit.poids(1));
        pm.addView(UiKit.boutonContour(a, "+", v -> plus.run()), new LinearLayout.LayoutParams(dp(64), dp(56)));
        return pm;
    }

    private LinearLayout carte() {
        LinearLayout l = UiKit.carte(a, 10);
        l.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        return l;
    }
}
