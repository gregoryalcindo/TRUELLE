package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Categories;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Heures;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Postes;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.ui.Libelles;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Une section du chantier ouverte depuis sa fiche : devis, dépenses, main d'œuvre, travaux en plus,
 * factures et paiements, tâches, notes ou réglages (un sujet par page, plus d'onglets).
 * Devis · Dépenses · Heures · Travaux + · Facturation · Réglages,
 * identiques pour un chantier pro ou entre proches (ce dernier, simplement sans TVA).
 */
public final class GererScreen extends Screen {

    public static final String DEVIS = "devis";
    public static final String DEPENSES = "depenses";
    public static final String HEURES = "heures";
    public static final String TRAVAUX = "travaux";
    public static final String FACTURATION = "facturation";
    public static final String REGLAGES = "reglages";
    public static final String NOTES = "notes";
    public static final String TACHES = "taches";

    private final String chantierId;
    private String onglet;

    // Réglages du chantier en cours d'édition (gardés si l'écran est redessiné).
    private String rTitre, rClient, rAdresse, rMode, rBudget, rType, rTel, rAutoliq;
    private int rMajoration = -1, rTva = -1, rRetenue = -1, rAvancement = -2;

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
        Finance f = a.finance(c);

        // Une section du chantier, ouverte depuis sa fiche (plus d'onglets : un seul sujet par page).
        p.entete.addView(UiKit.titre(a, titreSection(onglet), 30));

        switch (onglet) {
            case NOTES: notes(p, c); break;
            case TACHES: taches(p, c); break;
            case DEVIS: devis(p, f); break;
            case HEURES: heures(p, f); break;
            case TRAVAUX: travaux(p, f); break;
            case FACTURATION: facturation(p, f); break;
            case REGLAGES: reglages(p, c, f); break;
            default: depenses(p, f);
        }
        return p.racine;
    }

    static String titreSection(String onglet) {
        switch (onglet) {
            case DEVIS: return "Devis";
            case HEURES: return "Main d'œuvre";
            case TRAVAUX: return "Travaux en plus";
            case FACTURATION: return "Factures et paiements";
            case REGLAGES: return "Réglages du chantier";
            case NOTES: return "Notes";
            case TACHES: return "Tâches";
            default: return "Dépenses";
        }
    }

    // ------------------------------------------------------------- Tâches

    private void taches(Page p, Chantier c) {
        List<Chantier.Element> liste = c.taches();
        int faites = c.tachesFaites();
        if (!liste.isEmpty()) p.contenu.addView(UiKit.text(a, faites + " sur " + liste.size() + " faites", 16, UiKit.ENCRE_2, false));
        Blocs.Groupe g = new Blocs.Groupe(a);
        for (Chantier.Element t : liste) {
            g.ajouter(Blocs.rangeeTache(a, t, null, v -> Blocs.basculerTache(a, chantierId, t, () -> a.remplacer(this)),
                    v -> a.ouvrir(new TacheScreen(a, chantierId, t.id, null))));
        }
        if (g.vide()) p.contenu.addView(UiKit.text(a, "Aucune tâche. Notez ce qu'il reste à faire, qui s'en occupe et quand.", 16, UiKit.ENCRE_2, false));
        else p.contenu.addView(g.vue, UiKit.pleineLargeur());
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter une tâche", v -> a.ouvrir(new TacheScreen(a, chantierId, null, null))));
    }

    // ------------------------------------------------------------- Notes

    private void notes(Page p, Chantier c) {
        List<Chantier.Ajout> ajouts = c.derniersAjouts();
        Blocs.Groupe g = new Blocs.Groupe(a);
        for (Chantier.Ajout aj : ajouts) {
            if (!Event.NOTE.equals(aj.type)) continue;
            Chantier.Note n = c.note(aj.id);
            if (n == null) continue;
            String t = n.texte.replace('\n', ' ');
            g.ajouter(Blocs.rangee(a, null, t.length() > 80 ? t.substring(0, 80) + "…" : t,
                    FicheScreen.quand(n.creeLe) + (n.auteur.isEmpty() ? "" : " · " + n.auteur), null, true,
                    v -> a.ouvrir(new NoteScreen(a, chantierId, n.id))));
        }
        if (g.vide()) p.contenu.addView(UiKit.text(a, "Aucune note. Livraison, remarque du client, à ne pas oublier…", 16, UiKit.ENCRE_2, false));
        else p.contenu.addView(g.vue, UiKit.pleineLargeur());
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter une note", v -> a.ouvrir(new NoteScreen(a, chantierId, null))));
    }

    /** Bandeau cliquable : budget et bénéfice → tableau de bord. */
    private View resume(Finance f) {
        LinearLayout row = UiKit.ligne(a, 10);
        row.setPadding(dp(16), dp(12), dp(12), dp(12));
        row.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(14), 0, 0)));
        row.setClickable(true);
        LinearLayout textes = UiKit.colonne(a, 2);
        {
            Finance.Synthese s = f.prevision();
            long conso = f.consommeBp();
            textes.addView(UiKit.text(a, conso >= 0 ? "Budget consommé : " + Finance.pourcent(conso) : "Budget : à définir dans les devis", 16,
                    conso > 10000 ? UiKit.ROUGE : UiKit.ENCRE_2, conso > 10000));
            TextView b = UiKit.text(a, "Bénéfice estimé : " + Money.format(s.benefice), 19, s.benefice < 0 ? UiKit.ROUGE : UiKit.ENCRE, true);
            textes.addView(b);
            row.setContentDescription("Voir le budget et la rentabilité");
        }
        row.addView(textes, UiKit.poids(1));
        row.addView(UiKit.icone(a, R.drawable.ic_chevron, UiKit.ENCRE, 24));
        row.setOnClickListener(v -> a.ouvrir(new RentabiliteScreen(a, chantierId)));
        return row;
    }

    private View onglets(Chantier c) {
        String[][] liste = new String[][]{{DEVIS, "Devis"}, {DEPENSES, "Dépenses"}, {HEURES, "Heures"}, {TRAVAUX, "Travaux +"}, {FACTURATION, "Facturation"}, {REGLAGES, "Réglages"}};
        LinearLayout row = UiKit.ligne(a, 8);
        row.setPadding(0, dp(4), 0, 0);
        for (String[] o : liste) {
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

    private void devis(Page p, Finance f) {
        LinearLayout recap = carte();
        recap.addView(UiKit.ligneChiffre(a, "Signé par le client (hors TVA)", Money.format(f.devisSignesHt()), true), UiKit.pleineLargeur());
        recap.addView(UiKit.ligneChiffre(a, "Chiffré en cours (hors TVA)", Money.format(f.chiffreHt()), false), UiKit.pleineLargeur());
        if (f.sousTraitanceAccepteeCout() > 0) {
            recap.addView(UiKit.ligneChiffre(a, "Sous-traitance acceptée", Money.format(f.sousTraitanceAccepteeCout()), false), UiKit.pleineLargeur());
        }
        p.contenu.addView(recap, UiKit.pleineLargeur());

        p.contenu.addView(UiKit.section(a, "Nos devis"));
        int n = 0;
        for (Chantier.Element el : f.c.elements(Event.DEVIS)) {
            if (!Finance.estNotreDevis(el)) continue;
            n++;
            p.contenu.addView(ligneDevis(f, el), UiKit.pleineLargeur());
        }
        if (n == 0) p.contenu.addView(UiKit.text(a, "Aucun devis enregistré.", 16, UiKit.ENCRE_2, false));

        p.contenu.addView(UiKit.section(a, "Sous-traitants"));
        n = 0;
        for (Chantier.Element el : f.c.elements(Event.DEVIS)) {
            if (Finance.estNotreDevis(el)) continue;
            n++;
            p.contenu.addView(ligneDevis(f, el), UiKit.pleineLargeur());
        }
        if (n == 0) p.contenu.addView(UiKit.text(a, "Aucun devis de sous-traitant.", 16, UiKit.ENCRE_2, false));

        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter un devis", v -> a.ouvrir(new DevisScreen(a, chantierId, null))));
    }

    private View ligneDevis(Finance f, Chantier.Element el) {
        String titre = el.get("libelle").isEmpty() ? "Devis" : el.get("libelle");
        if (!el.get("version").isEmpty() && !"1".equals(el.get("version"))) titre += " (v" + el.get("version") + ")";
        Finance.Totaux t = f.totaux(el);
        String sous = el.get("prestataire");
        int nb = f.lignes(el).size();
        sous = (sous.isEmpty() ? "" : sous + " · ") + nb + (nb > 1 ? " lignes" : " ligne");
        if (Finance.autoliquidation(el)) sous += " · autoliquidation";
        if (!el.get("fichier").isEmpty()) sous += " · document joint";
        return UiKit.ligneListe(a, titre, sous, Money.format(t.ttc),
                Libelles.chipDevis(a, el.get("sorte"), el.get("statut")),
                v -> a.ouvrir(new DevisScreen(a, chantierId, el.id)));
    }

    // ------------------------------------------------------------- Dépenses

    private void depenses(Page p, Finance f) {
        Chantier c = f.c;
        List<Chantier.Depense> liste = c.depenses();
        int aVerifier = f.aVerifier();
        if (aVerifier > 0) {
            LinearLayout alerte = carte();
            alerte.addView(UiKit.text(a, aVerifier + (aVerifier > 1 ? " dépenses à vérifier" : " dépense à vérifier"), 18, UiKit.AMBRE, true));
            alerte.addView(UiKit.text(a, "Saisies sur le terrain ou lues automatiquement : vérifiez le montant et la TVA, puis validez.", 15, UiKit.ENCRE_2, false));
            alerte.addView(UiKit.boutonContour(a, "Tout valider", v -> new AlertDialog.Builder(a)
                    .setTitle("Valider les " + aVerifier + " dépenses ?")
                    .setMessage("Elles passeront en « Validée ». Vous pourrez toujours les corriger.")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Valider", (d, w) -> {
                        for (Chantier.Depense dep : c.depenses()) {
                            if (dep.valide) continue;
                            Map<String, String> data = new LinkedHashMap<>();
                            data.put("cible", dep.id);
                            data.put("valide", "oui");
                            enregistrer(chantierId, Event.CORRECTION, data);
                        }
                        toast("Dépenses validées ✓");
                        a.remplacer(this);
                    }).show()), UiKit.pleineLargeur());
            p.contenu.addView(alerte, UiKit.pleineLargeur());
        }

        long total = 0;
        for (Chantier.Depense d : liste) total += d.montantCentimes;
        p.contenu.addView(UiKit.ligneChiffre(a, liste.size() + (liste.size() > 1 ? " dépenses (TTC)" : " dépense (TTC)"), Money.format(total), true), UiKit.pleineLargeur());
        if (liste.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucune dépense pour l'instant.", 16, UiKit.ENCRE_2, false));
        else {
            LinearLayout parCat = carte();
            for (String[] cat : Categories.TOUTES) {
                long t = c.totalCategorie(cat[0]);
                if (t > 0) parCat.addView(UiKit.ligneChiffre(a, cat[1], Money.format(t), false), UiKit.pleineLargeur());
            }
            p.contenu.addView(parCat, UiKit.pleineLargeur());
        }
        for (int i = liste.size() - 1; i >= 0; i--) {
            Chantier.Depense d = liste.get(i);
            TextView chip = !d.valide ? UiKit.chip(a, "À vérifier", UiKit.AMBRE_FOND, UiKit.AMBRE)
                    : c.estARefacturer(d) ? UiKit.chipContour(a, "À refacturer")
                    : UiKit.chip(a, "Interne", UiKit.GRIS_FOND, UiKit.GRIS);
            String sous = Categories.libelle(d.categorie) + (d.date.isEmpty() ? "" : " · " + d.date)
                    + (c.entreProches() ? "" : " · TVA " + Tva.libelle(Finance.tvaDepense(d)))
                    + (d.fichier.isEmpty() ? "" : " · facture jointe");
            p.contenu.addView(UiKit.ligneListe(a, d.fournisseur.isEmpty() ? "Dépense" : d.fournisseur, sous, Money.format(d.montantCentimes), chip,
                    v -> a.ouvrir(new DepenseScreen(a, chantierId, d.id))), UiKit.pleineLargeur());
        }
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter une dépense", v -> a.scannerFacture(new Scanner.Callback() {
            @Override
            public void onFacture(Scanner.Facture fac) {
                a.ouvrir(DepenseScreen.avecFacture(a, chantierId, fac));
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

    // ------------------------------------------------------------- Heures

    private void heures(Page p, Finance f) {
        List<Chantier.Heure> liste = f.c.heures();
        long cout = 0;
        for (Chantier.Heure h : liste) cout += f.coutHeure(h);
        LinearLayout recap = carte();
        recap.addView(UiKit.ligneChiffre(a, "Total des heures", Heures.format(f.totalHeuresCentiemes()), true), UiKit.pleineLargeur());
        recap.addView(UiKit.ligneChiffre(a, "Coût de la main d'œuvre", Money.format(cout), true), UiKit.pleineLargeur());
        long budget = f.prevuParPoste().get(Postes.MAIN_OEUVRE);
        if (budget > 0) recap.addView(UiKit.ligneChiffre(a, "Budget main d'œuvre", Money.format(budget), false), UiKit.pleineLargeur());
        if (f.r.tauxHoraireCentimes <= 0) {
            recap.addView(UiKit.text(a, "Réglez le coût horaire chargé dans les réglages de l'appli pour chiffrer les heures.", 15, UiKit.ENCRE_2, false));
        }
        p.contenu.addView(recap, UiKit.pleineLargeur());
        if (liste.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucune heure saisie.", 16, UiKit.ENCRE_2, false));
        for (Chantier.Heure h : liste) {
            String titre = Heures.format(h.heuresCentiemes) + (h.personne.isEmpty() ? "" : " · " + h.personne);
            String sous = (h.date.isEmpty() ? "" : h.date) + (h.note.isEmpty() ? "" : (h.date.isEmpty() ? "" : " · ") + h.note);
            p.contenu.addView(UiKit.ligneListe(a, titre, sous, Money.format(f.coutHeure(h)), null,
                    v -> a.ouvrir(new HeuresScreen(a, chantierId, h.id))), UiKit.pleineLargeur());
        }
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter des heures", v -> a.ouvrir(new HeuresScreen(a, chantierId, null))));
    }

    // ------------------------------------------------------------- Travaux en plus

    private void travaux(Page p, Finance f) {
        long total = 0;
        for (Chantier.Element el : f.c.elements(Event.TRAVAUX)) if (Finance.accepte(el)) total += f.ttcTravaux(el);
        p.contenu.addView(UiKit.ligneChiffre(a, "Travaux en plus acceptés" + ttc(f), Money.format(total), true), UiKit.pleineLargeur());
        List<Chantier.Element> liste = f.c.elements(Event.TRAVAUX);
        if (liste.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucun travail pour l'instant.", 16, UiKit.ENCRE_2, false));
        for (Chantier.Element el : liste) {
            String sous = el.get("signe_le").isEmpty() ? (Chantier.TRAVAUX_PROPOSE.equals(el.get("statut")) ? "En attente d'accord" : "") : "Signé le " + el.get("signe_le");
            p.contenu.addView(UiKit.ligneListe(a, el.get("description"), sous, Money.format(f.ttcTravaux(el)),
                    Libelles.chipTravaux(a, el.get("statut")), v -> a.ouvrir(new TravauxScreen(a, chantierId, el.id))), UiKit.pleineLargeur());
        }
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter des travaux", v -> a.ouvrir(new TravauxScreen(a, chantierId, null))));
    }

    // ------------------------------------------------------------- Facturation / versements

    private void facturation(Page p, Finance f) {
        LinearLayout recap = carte();
        com.gregory.truelle.data.RecapFin r = com.gregory.truelle.data.RecapFin.construire(f, false);
        {
            recap.addView(UiKit.ligneChiffre(a, "Montant du chantier" + ttc(f), Money.format(r.totalTtc), true), UiKit.pleineLargeur());
            recap.addView(UiKit.ligneChiffre(a, "Facturé" + ttc(f), Money.format(f.factureTtc()), false), UiKit.pleineLargeur());
            recap.addView(UiKit.ligneChiffre(a, "Encaissé", Money.format(f.encaisse()), false), UiKit.pleineLargeur());
            if (f.enAttenteDePaiement() > 0) recap.addView(UiKit.ligneChiffre(a, "En attente de paiement", Money.format(f.enAttenteDePaiement()), false), UiKit.pleineLargeur());
            if (f.retenues() > 0) recap.addView(UiKit.ligneChiffre(a, "Retenues de garantie à récupérer", Money.format(f.retenues()), false), UiKit.pleineLargeur());
            recap.addView(UiKit.ligneChiffre(a, "Reste à facturer" + ttc(f), Money.format(Math.max(0, r.totalTtc - f.factureTtc())), true), UiKit.pleineLargeur());
        }
        p.contenu.addView(recap, UiKit.pleineLargeur());

        List<Chantier.Element> liste = f.c.elements(Event.FACTURATION);
        if (liste.isEmpty()) p.contenu.addView(UiKit.text(a, "Aucune ligne. Ajoutez l'acompte, les situations et le solde.", 16, UiKit.ENCRE_2, false));
        for (Chantier.Element el : liste) {
            String sous = (el.get("numero").isEmpty() ? "" : "Facture n° " + el.get("numero"))
                    + (el.get("date_encaisse").isEmpty() ? (el.get("date_facture").isEmpty() ? "" : " · facturé le " + el.get("date_facture")) : " · payé le " + el.get("date_encaisse"));
            if (f.retenue(el) > 0) sous += " · retenue " + Money.format(f.retenue(el));
            TextView chip = Libelles.chipFacturation(a, el.get("statut"));
            p.contenu.addView(UiKit.ligneListe(a, el.get("libelle"), sous.startsWith(" · ") ? sous.substring(3) : sous, Money.format(f.ttcFacture(el)),
                    chip, v -> a.ouvrir(new FacturationScreen(a, chantierId, el.id))), UiKit.pleineLargeur());
        }
        p.contenu.addView(UiKit.text(a, "Suivi interne : la vraie facture est faite à part, notez ici son numéro pour faire le lien.", 15, UiKit.ENCRE_2, false));
        p.ajouterAuPied(UiKit.boutonAction(a, "+ Ajouter une ligne", v -> a.ouvrir(new FacturationScreen(a, chantierId, null))));
        p.ajouterAuPied(UiKit.boutonContour(a, "PDF du chantier", v -> a.ouvrir(new FinChantierScreen(a, chantierId))));
    }

    // ------------------------------------------------------------- Réglages du chantier

    private void reglages(Page p, Chantier c, Finance f) {
        if (rTitre == null) {
            rTitre = c.titre;
            rClient = c.client;
            rAdresse = c.adresse;
            rTel = c.telephone;
            rAutoliq = c.autoliquidation ? "oui" : "non";
            rMode = c.mode;
            rType = c.type;
            rMajoration = c.majorationPct;
            rTva = f.tvaDefaut();
            rRetenue = c.retenueBp;
            rAvancement = c.avancementBp;
            rBudget = c.budgetMaterielCentimes > 0 ? Money.toInput(c.budgetMaterielCentimes) : "";
        }
        boolean proches = Chantier.TYPE_PROCHES.equals(rType);
        EditText fTitre = UiKit.champTexte(a, rTitre);
        EditText fClient = UiKit.champ(a, rClient, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText fAdresse = UiKit.champ(a, rAdresse, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText fTel = UiKit.champ(a, rTel, InputType.TYPE_CLASS_PHONE);
        fTel.setHint("Pour l'appeler depuis la fiche");
        EditText fBudget = UiKit.champMontant(a, rBudget);
        fBudget.setHint("Facultatif");
        Runnable garder = () -> {
            rTitre = fTitre.getText().toString().trim();
            rClient = fClient.getText().toString().trim();
            rAdresse = fAdresse.getText().toString().trim();
            rTel = fTel.getText().toString().trim();
            rBudget = fBudget.getText().toString().trim();
        };

        // Type de chantier
        LinearLayout blocType = UiKit.colonne(a, 8);
        blocType.addView(UiKit.titre(a, "Type de chantier", 20));
        blocType.addView(UiKit.choix(a, new String[][]{{Chantier.TYPE_PRO, "Professionnel"}, {Chantier.TYPE_PROCHES, "Entre proches"}}, rType, choisi -> {
            garder.run();
            rType = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        blocType.addView(UiKit.text(a, proches
                ? "Fonctionne comme un chantier pro (devis, budget, bénéfice, paiements), mais sans aucune TVA."
                : "Chantier déclaré : devis, TVA, budget, bénéfice et facturation.", 15, UiKit.ENCRE_2, false));
        p.contenu.addView(carte(blocType), UiKit.pleineLargeur());

        // Avancement
        LinearLayout blocAv = UiKit.colonne(a, 8);
        blocAv.addView(UiKit.titre(a, "Avancement des travaux", 20));
        blocAv.addView(plusMoins(rAvancement < 0 ? "Non renseigné" : Finance.pourcent(rAvancement), () -> {
            garder.run();
            rAvancement = rAvancement < 0 ? 0 : Math.max(0, rAvancement - 1000);
            a.remplacer(this);
        }, () -> {
            garder.run();
            rAvancement = rAvancement < 0 ? 1000 : Math.min(10000, rAvancement + 1000);
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        blocAv.addView(UiKit.text(a, "Par pas de 10 %. Comparé au budget consommé pour repérer un dérapage.", 15, UiKit.ENCRE_2, false));
        p.contenu.addView(carte(blocAv), UiKit.pleineLargeur());

        {
            // Matériel
            LinearLayout blocMode = UiKit.colonne(a, 8);
            blocMode.addView(UiKit.titre(a, "Matériel", 20));
            blocMode.addView(UiKit.choix(a, new String[][]{{Chantier.MODE_REEL, "Refacturé au réel"}, {Chantier.MODE_FORFAIT, "Inclus au forfait"}}, rMode, choisi -> {
                garder.run();
                rMode = choisi;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            blocMode.addView(UiKit.text(a, "Majoration du matériel refacturé", 16, UiKit.ENCRE_2, false));
            blocMode.addView(plusMoins(rMajoration + " %", () -> {
                garder.run();
                rMajoration = Math.max(0, rMajoration - 1);
                a.remplacer(this);
            }, () -> {
                garder.run();
                rMajoration = Math.min(100, rMajoration + 1);
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            p.contenu.addView(carte(blocMode), UiKit.pleineLargeur());

            // TVA et retenue
            LinearLayout blocTva = UiKit.colonne(a, 8);
            blocTva.addView(UiKit.titre(a, proches ? "Garantie" : "TVA et garantie", 20));
            if (!proches) {
                blocTva.addView(UiKit.text(a, "Pour qui travaillez-vous ?", 16, UiKit.ENCRE_2, false));
                blocTva.addView(UiKit.choix(a, new String[][]{{"non", "Un particulier ou une entreprise"}, {"oui", "Une entreprise du bâtiment (sous-traitance)"}}, rAutoliq, choisi -> {
                    garder.run();
                    rAutoliq = choisi;
                    a.remplacer(this);
                }), UiKit.pleineLargeur());
            }
            if (proches) {
                blocTva.addView(UiKit.text(a, "Chantier entre proches : aucune TVA, sur les devis comme sur les paiements.", 15, UiKit.ENCRE_2, false));
            } else if ("oui".equals(rAutoliq)) {
                blocTva.addView(UiKit.text(a, "Sous-traitance du bâtiment : vos devis et factures sont hors taxes, avec la mention « Autoliquidation ». "
                        + "C'est l'entreprise qui vous fait travailler qui déclare la TVA.", 15, UiKit.ENCRE_2, false));
            } else if (f.r.franchise()) {
                blocTva.addView(UiKit.text(a, "Entreprise en franchise de TVA (réglages de l'appli) : pas de TVA facturée.", 15, UiKit.ENCRE_2, false));
            } else {
                blocTva.addView(UiKit.blocTva(a, "TVA proposée par défaut sur ce chantier", rTva,
                        "20 % : neuf, locaux pros · 10 % : rénovation d'un logement de plus de 2 ans · 5,5 % : rénovation énergétique.", t -> {
                            garder.run();
                            rTva = t;
                            a.remplacer(this);
                        }), UiKit.pleineLargeur());
            }
            blocTva.addView(UiKit.text(a, "Retenue de garantie (max. 5 %, rendue 1 an après la réception)", 16, UiKit.ENCRE_2, false));
            blocTva.addView(plusMoins(Tva.libelle(rRetenue), () -> {
                garder.run();
                rRetenue = Math.max(0, rRetenue - 100);
                a.remplacer(this);
            }, () -> {
                garder.run();
                rRetenue = Math.min(500, rRetenue + 100);
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            p.contenu.addView(carte(blocTva), UiKit.pleineLargeur());
            p.contenu.addView(UiKit.libelle(a, "Budget matériaux (si les devis n'ont pas de coût prévu)", UiKit.euro(a, fBudget)), UiKit.pleineLargeur());
        }

        p.contenu.addView(UiKit.libelle(a, "Travaux", fTitre), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Client", fClient), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Téléphone du client", fTel), UiKit.pleineLargeur());
        p.contenu.addView(UiKit.libelle(a, "Adresse", fAdresse), UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            garder.run();
            if (rTitre.isEmpty()) {
                fTitre.setError("Indiquez les travaux");
                return;
            }
            long budget = rBudget.isEmpty() ? 0 : Money.parseCentimes(rBudget);
            if (budget < 0) {
                toast("Montant invalide");
                return;
            }
            Map<String, String> data = new LinkedHashMap<>();
            if (!rTitre.equals(c.titre)) data.put("titre", rTitre);
            if (!rClient.equals(c.client)) data.put("client", rClient);
            if (!rAdresse.equals(c.adresse)) data.put("adresse", rAdresse);
            if (!rTel.equals(c.telephone)) data.put("telephone", rTel);
            if ("oui".equals(rAutoliq) != c.autoliquidation) data.put("autoliquidation", rAutoliq);
            if (!rType.equals(c.type)) data.put("type", rType);
            if (!rMode.equals(c.mode)) data.put("mode", rMode);
            if (rMajoration != c.majorationPct) data.put("majoration_pct", Integer.toString(rMajoration));
            if (budget != c.budgetMaterielCentimes) data.put("budget_materiel_centimes", Long.toString(budget));
            if (!proches && !"oui".equals(rAutoliq) && !f.r.franchise() && rTva != f.tvaDefaut()) data.put("tva_defaut_bp", Integer.toString(rTva));
            if (rRetenue != c.retenueBp) data.put("retenue_bp", Integer.toString(rRetenue));
            if (rAvancement != c.avancementBp && rAvancement >= 0) data.put("avancement_bp", Integer.toString(rAvancement));
            if (data.isEmpty() || enregistrer(chantierId, Event.CHANTIER_MODIFIE, data)) {
                toast("Réglages enregistrés ✓");
                rTitre = null;
                a.retour();
            }
        }));
    }

    /** « (TTC) » seulement quand il y a de la TVA. */
    private static String ttc(Finance f) {
        return f.sansTvaFacturee() ? "" : " (TTC)";
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
        LinearLayout l = UiKit.carte(a, 8);
        l.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        return l;
    }

    private View carte(View contenu) {
        LinearLayout c = UiKit.carte(a, 0);
        c.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        c.addView(contenu, UiKit.pleineLargeur());
        return c;
    }
}
