package com.gregory.truelle.ui.screens;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Libelles;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Écran 6 des maquettes : « Gérer le chantier », le niveau poussé, en onglets. */
public final class GererScreen extends Screen {

    public static final String DEVIS = "devis";
    public static final String DEPENSES = "depenses";
    public static final String TRAVAUX = "travaux";
    public static final String FACTURATION = "facturation";
    public static final String REGLAGES = "reglages";

    private static final String[][] ONGLETS = {
            {DEVIS, "Devis"}, {DEPENSES, "Dépenses"}, {TRAVAUX, "Travaux +"}, {FACTURATION, "Facturation"}, {REGLAGES, "Réglages"},
    };

    private final String chantierId;
    private String onglet;

    // Réglages en cours d'édition (gardés si l'écran est redessiné).
    private String rTitre, rClient, rAdresse, rMode, rBudget;
    private int rMajoration = -1;

    public GererScreen(MainActivity a, String chantierId, String onglet) {
        super(a);
        this.chantierId = chantierId;
        this.onglet = onglet == null ? DEPENSES : onglet;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return !REGLAGES.equals(onglet);
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, c == null ? "Retour" : c.titre, v -> a.retour()));
        if (c == null) return p.racine;

        LinearLayout ligneTitre = UiKit.ligne(a, 12);
        ligneTitre.addView(UiKit.titre(a, "Gérer le chantier", 30), UiKit.poids(1));
        ligneTitre.addView(UiKit.chipContour(a, Chantier.MODE_REEL.equals(c.mode) ? "Réel" : "Forfait"));
        p.entete.addView(ligneTitre, UiKit.pleineLargeur());
        p.entete.addView(resumeMarge(c), UiKit.pleineLargeur());
        p.entete.addView(onglets());

        switch (onglet) {
            case DEVIS: devis(p, c); break;
            case TRAVAUX: travaux(p, c); break;
            case FACTURATION: facturation(p, c); break;
            case REGLAGES: reglages(p, c); break;
            default: depenses(p, c);
        }
        return p.racine;
    }

    /** Bandeau cliquable « Marge actuelle » → écran Rentabilité. */
    private View resumeMarge(Chantier c) {
        LinearLayout row = UiKit.ligne(a, 10);
        row.setPadding(dp(16), dp(12), dp(12), dp(12));
        row.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(14), 0, 0)));
        row.setClickable(true);
        row.setContentDescription("Voir la rentabilité");
        row.addView(UiKit.text(a, "Marge actuelle", 17, UiKit.ENCRE_2, false), UiKit.poids(1));
        TextView m = UiKit.text(a, Money.format(c.marge()), 19, c.marge() < 0 ? UiKit.ROUGE : UiKit.ENCRE, true);
        row.addView(m);
        row.addView(UiKit.icone(a, R.drawable.ic_chevron, UiKit.ENCRE, 24));
        row.setOnClickListener(v -> a.ouvrir(new RentabiliteScreen(a, chantierId)));
        return row;
    }

    private View onglets() {
        LinearLayout row = UiKit.ligne(a, 8);
        row.setPadding(0, dp(4), 0, 0);
        for (String[] o : ONGLETS) {
            boolean sel = o[0].equals(onglet);
            Button b = UiKit.bouton(a, o[1], sel ? UiKit.ENCRE : Color.TRANSPARENT, sel ? Color.WHITE : UiKit.ENCRE, 48, v -> {
                onglet = o[0];
                a.remplacer(this);
            });
            b.setTextSize(17);
            b.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b.setBackground(UiKit.ripple(UiKit.rounded(sel ? UiKit.ENCRE : Color.TRANSPARENT, dp(24), sel ? 0 : dp(2), UiKit.BORDURE)));
            row.addView(b);
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    // ------------------------------------------------------------- Devis

    private void devis(Page p, Chantier c) {
        p.contenu.addView(UiKit.ligneChiffre(a, "Montant signé par le client", Money.format(c.totalDevisSignes()), true), UiKit.pleineLargeur());

        p.contenu.addView(UiKit.section(a, "Nos devis"));
        int n = 0;
        for (Chantier.Element el : c.elements(Event.DEVIS)) {
            if (!Chantier.DEVIS_NOUS.equals(el.get("sorte"))) continue;
            n++;
            p.contenu.addView(ligneDevis(el), UiKit.pleineLargeur());
        }
        if (n == 0) p.contenu.addView(UiKit.text(a, "Aucun devis enregistré.", 16, UiKit.ENCRE_2, false));

        p.contenu.addView(UiKit.section(a, "Sous-traitants"));
        n = 0;
        for (Chantier.Element el : c.elements(Event.DEVIS)) {
            if (!Chantier.DEVIS_SOUS_TRAITANT.equals(el.get("sorte"))) continue;
            n++;
            p.contenu.addView(ligneDevis(el), UiKit.pleineLargeur());
        }
        if (n == 0) p.contenu.addView(UiKit.text(a, "Aucun devis de sous-traitant.", 16, UiKit.ENCRE_2, false));

        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter un devis", v -> a.ouvrir(new DevisScreen(a, chantierId, null))));
    }

    private View ligneDevis(Chantier.Element el) {
        String titre = el.get("libelle").isEmpty() ? "Devis" : el.get("libelle");
        String sous = el.get("prestataire");
        if (!el.get("fichier").isEmpty()) sous = sous.isEmpty() ? "document joint" : sous + " · document joint";
        return UiKit.ligneListe(a, titre, sous, Money.format(el.centimes("montant_centimes")),
                Libelles.chipDevis(a, el.get("sorte"), el.get("statut")),
                v -> a.ouvrir(new DevisScreen(a, chantierId, el.id)));
    }

    // ------------------------------------------------------------- Dépenses

    private void depenses(Page p, Chantier c) {
        List<Chantier.Depense> liste = c.depenses();
        p.contenu.addView(UiKit.ligneChiffre(a, liste.size() + (liste.size() > 1 ? " dépenses" : " dépense"), "Total : " + Money.format(c.totalDepensesCentimes()), true), UiKit.pleineLargeur());
        if (liste.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucune dépense pour l'instant.", 16, UiKit.ENCRE_2, false));
        else {
            LinearLayout parCat = UiKit.carte(a, 6);
            parCat.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
            for (String[] cat : com.gregory.truelle.data.Categories.TOUTES) {
                long t = c.totalCategorie(cat[0]);
                if (t > 0) parCat.addView(UiKit.ligneChiffre(a, cat[1], Money.format(t), false), UiKit.pleineLargeur());
            }
            p.contenu.addView(parCat, UiKit.pleineLargeur());
        }
        for (int i = liste.size() - 1; i >= 0; i--) {
            Chantier.Depense d = liste.get(i);
            boolean refact = c.estARefacturer(d);
            TextView chip = refact ? UiKit.chipContour(a, "À refacturer") : UiKit.chip(a, "Interne", UiKit.GRIS_FOND, UiKit.GRIS);
            String sous = com.gregory.truelle.data.Categories.libelle(d.categorie) + (d.date.isEmpty() ? "" : " · " + d.date) + (d.fichier.isEmpty() ? "" : " · facture jointe");
            p.contenu.addView(UiKit.ligneListe(a, d.fournisseur.isEmpty() ? "Dépense" : d.fournisseur, sous, Money.format(d.montantCentimes), chip,
                    v -> a.ouvrir(new DepenseScreen(a, chantierId, d.id))), UiKit.pleineLargeur());
        }
        p.contenu.addView(UiKit.text(a, "Toucher une dépense pour la corriger, joindre sa facture ou changer « À refacturer ».", 15, UiKit.ENCRE_2, false));
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter une dépense", v -> a.scannerFacture(new Scanner.Callback() {
            @Override
            public void onFacture(Scanner.Facture f) {
                a.ouvrir(DepenseScreen.avecFacture(a, chantierId, f));
            }

            @Override
            public void onAnnule() {
                a.ouvrir(new DepenseScreen(a, chantierId, null));
            }

            @Override
            public void onErreur(String message) {
                toast(message);
                a.ouvrir(new DepenseScreen(a, chantierId, null));
            }
        })));
    }

    // ------------------------------------------------------------- Travaux en plus

    private void travaux(Page p, Chantier c) {
        p.contenu.addView(UiKit.ligneChiffre(a, "Travaux en plus acceptés", Money.format(c.totalTravauxAcceptes()), true), UiKit.pleineLargeur());
        List<Chantier.Element> liste = c.elements(Event.TRAVAUX);
        if (liste.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucun travail en plus pour l'instant.", 16, UiKit.ENCRE_2, false));
        for (Chantier.Element el : liste) {
            String sous = el.get("signe_le").isEmpty() ? (Chantier.TRAVAUX_PROPOSE.equals(el.get("statut")) ? "En attente d'accord" : "") : "Signé le " + el.get("signe_le");
            p.contenu.addView(UiKit.ligneListe(a, el.get("description"), sous, Money.format(el.centimes("montant_centimes")),
                    Libelles.chipTravaux(a, el.get("statut")), v -> a.ouvrir(new TravauxScreen(a, chantierId, el.id))), UiKit.pleineLargeur());
        }
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter des travaux", v -> a.ouvrir(new TravauxScreen(a, chantierId, null))));
    }

    // ------------------------------------------------------------- Facturation

    private void facturation(Page p, Chantier c) {
        LinearLayout recap = UiKit.carte(a, 8);
        recap.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        long vendu = c.montantVendu();
        recap.addView(UiKit.ligneChiffre(a, "Montant du chantier", Money.format(vendu), true), UiKit.pleineLargeur());
        recap.addView(UiKit.ligneChiffre(a, "Facturé", Money.format(c.totalFacture()), false), UiKit.pleineLargeur());
        recap.addView(UiKit.ligneChiffre(a, "Encaissé", Money.format(c.totalEncaisse()), false), UiKit.pleineLargeur());
        recap.addView(UiKit.ligneChiffre(a, "Reste à facturer", Money.format(Math.max(0, vendu - c.totalFacture())), false), UiKit.pleineLargeur());
        p.contenu.addView(recap, UiKit.pleineLargeur());

        List<Chantier.Element> liste = c.elements(Event.FACTURATION);
        if (liste.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucune ligne. Ajoutez l'acompte, les situations et le solde.", 16, UiKit.ENCRE_2, false));
        for (Chantier.Element el : liste) {
            String sous = el.get("numero").isEmpty() ? "" : "Facture n° " + el.get("numero");
            p.contenu.addView(UiKit.ligneListe(a, el.get("libelle"), sous, Money.format(el.centimes("montant_centimes")),
                    Libelles.chipFacturation(a, el.get("statut")), v -> a.ouvrir(new FacturationScreen(a, chantierId, el.id))), UiKit.pleineLargeur());
        }
        p.contenu.addView(UiKit.text(a, "Suivi interne : la vraie facture est faite à part, notez ici son numéro pour faire le lien.", 15, UiKit.ENCRE_2, false));
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter une ligne", v -> a.ouvrir(new FacturationScreen(a, chantierId, null))));
        p.ajouterAuPied(UiKit.boutonContour(a, "PDF du chantier", v -> a.ouvrir(new FinChantierScreen(a, chantierId))));
    }

    // ------------------------------------------------------------- Réglages

    private void reglages(Page p, Chantier c) {
        if (rTitre == null) {
            rTitre = c.titre;
            rClient = c.client;
            rAdresse = c.adresse;
            rMode = c.mode;
            rMajoration = c.majorationPct;
            rBudget = c.budgetMaterielCentimes > 0 ? Money.toInput(c.budgetMaterielCentimes) : "";
        }
        EditText fTitre = UiKit.champTexte(a, rTitre);
        EditText fClient = UiKit.champ(a, rClient, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText fAdresse = UiKit.champ(a, rAdresse, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText fBudget = UiKit.champMontant(a, rBudget);
        fBudget.setHint("Facultatif");
        Runnable garder = () -> {
            rTitre = fTitre.getText().toString().trim();
            rClient = fClient.getText().toString().trim();
            rAdresse = fAdresse.getText().toString().trim();
            rBudget = fBudget.getText().toString().trim();
        };

        // Matériel
        LinearLayout blocMode = UiKit.colonne(a, 8);
        blocMode.addView(UiKit.titre(a, "Matériel", 20));
        blocMode.addView(UiKit.choix(a, new String[][]{{Chantier.MODE_REEL, "Refacturé au réel"}, {Chantier.MODE_FORFAIT, "Inclus au forfait"}}, rMode, choisi -> {
            garder.run();
            rMode = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        blocMode.addView(UiKit.text(a, Chantier.MODE_REEL.equals(rMode)
                ? "Les factures fournisseurs seront jointes au PDF client."
                : "Les dépenses restent internes, sauf celles marquées « À refacturer ».", 15, UiKit.ENCRE_2, false));
        p.contenu.addView(carte(blocMode), UiKit.pleineLargeur());

        // Majoration
        LinearLayout blocMaj = UiKit.colonne(a, 8);
        blocMaj.addView(UiKit.titre(a, "Majoration du matériel", 20));
        LinearLayout pm = UiKit.ligne(a, 10);
        pm.addView(UiKit.boutonContour(a, "−", v -> {
            garder.run();
            rMajoration = Math.max(0, rMajoration - 1);
            a.remplacer(this);
        }), new LinearLayout.LayoutParams(dp(64), dp(56)));
        TextView val = UiKit.titre(a, rMajoration + " %", 26);
        val.setGravity(android.view.Gravity.CENTER);
        pm.addView(val, UiKit.poids(1));
        pm.addView(UiKit.boutonContour(a, "+", v -> {
            garder.run();
            rMajoration = Math.min(100, rMajoration + 1);
            a.remplacer(this);
        }), new LinearLayout.LayoutParams(dp(64), dp(56)));
        blocMaj.addView(pm, UiKit.pleineLargeur());
        blocMaj.addView(UiKit.text(a, "Appliquée au matériel refacturé (prix d'achat + majoration).", 15, UiKit.ENCRE_2, false));
        p.contenu.addView(carte(blocMaj), UiKit.pleineLargeur());

        // Budget et infos
        p.contenu.addView(UiKit.libelle(a, "Budget matériel prévu", UiKit.euro(a, fBudget)), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Travaux", fTitre), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Client", fClient), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Adresse", fAdresse), UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            if (rTitre.isEmpty()) {
                fTitre.setError("Indiquez les travaux");
                return;
            }
            long budget = rBudget.isEmpty() ? 0 : Money.parseCentimes(rBudget);
            if (budget < 0) {
                fBudget.setError("Montant invalide");
                return;
            }
            Map<String, String> data = new LinkedHashMap<>();
            if (!rTitre.equals(c.titre)) data.put("titre", rTitre);
            if (!rClient.equals(c.client)) data.put("client", rClient);
            if (!rAdresse.equals(c.adresse)) data.put("adresse", rAdresse);
            if (!rMode.equals(c.mode)) data.put("mode", rMode);
            if (rMajoration != c.majorationPct) data.put("majoration_pct", Integer.toString(rMajoration));
            if (budget != c.budgetMaterielCentimes) data.put("budget_materiel_centimes", Long.toString(budget));
            if (data.isEmpty() || enregistrer(chantierId, Event.CHANTIER_MODIFIE, data)) {
                toast("Réglages enregistrés ✓");
                rTitre = null;
                onglet = DEPENSES;
                a.remplacer(this);
            }
        }));
    }

    private View carte(View contenu) {
        LinearLayout c = UiKit.carte(a, 0);
        c.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        c.addView(contenu, UiKit.pleineLargeur());
        return c;
    }
}
