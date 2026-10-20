package com.gregory.truelle.ui.screens;

import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Carnet;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Dates;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.sync.SyncManager;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/**
 * Carnet : le tableau de bord de toute l'activité.
 * D'abord la journée (tâches, alertes), puis l'argent, les chantiers en cours, l'activité récente.
 */
public final class CarnetScreen extends Screen {

    public CarnetScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean suitLaSynchro() {
        return true;
    }

    @Override
    public View build() {
        List<Chantier> tous = store().chantiers();
        Carnet k = Carnet.construire(tous, a.reglages());
        Page p = new Page(a);

        String jour = Dates.long_(Dates.aujourdhuiDate());
        p.entete.addView(UiKit.text(a, "Carnet · " + jour, 15, UiKit.ENCRE_2, false));
        String prenom = a.auteur();
        p.entete.addView(UiKit.titre(a, prenom.isEmpty() ? "Bonjour" : "Bonjour " + prenom, 32));
        p.entete.addView(etatDrive());

        // --- Aujourd'hui
        p.contenu.addView(Blocs.titreSection(a, "Aujourd'hui", k.nombreAFaire() == 0 ? null
                : k.nombreAFaire() + (k.nombreAFaire() > 1 ? " choses" : " chose"), null), UiKit.pleineLargeur());
        Blocs.Groupe g = new Blocs.Groupe(a);
        for (Carnet.Tache t : k.taches) {
            g.ajouter(Blocs.rangeeTache(a, t.tache, t.chantier.titre,
                    v -> Blocs.basculerTache(a, t.chantier.id, t.tache, () -> a.remplacer(this)),
                    v -> a.ouvrir(new TacheScreen(a, t.chantier.id, t.tache.id, null))));
        }
        for (Carnet.Alerte al : k.alertes) g.ajouter(rangeeAlerte(al));
        if (g.vide()) {
            LinearLayout calme = UiKit.ligne(a, 12);
            calme.setPadding(dp(16), dp(14), dp(16), dp(14));
            calme.addView(Blocs.pastille(a, R.drawable.ic_check, UiKit.VERT_FOND, UiKit.VERT, 32));
            calme.addView(UiKit.text(a, "Rien d'urgent. Les tâches datées d'aujourd'hui apparaîtront ici.", 16, UiKit.ENCRE_2, false), UiKit.poids(1));
            g.ajouter(calme);
        }
        p.contenu.addView(g.vue, UiKit.pleineLargeur());

        // --- L'argent
        p.contenu.addView(carteEncaisser(k), UiKit.pleineLargeur());
        LinearLayout duo = UiKit.ligne(a, 10);
        Long evo = k.evolutionPct();
        duo.addView(tuileChiffre("Encaissé en " + Dates.mois(Dates.aujourdhuiDate().getMonthValue()), Money.formatCourt(k.encaisseMois),
                evo == null ? "" : (evo >= 0 ? "↑ " : "↓ ") + Math.abs(evo) + " % vs mois dernier"), UiKit.poids(1));
        duo.addView(tuileChiffre("Bénéfice estimé", k.chantiersEstimes == 0 ? "—" : Money.formatCourt(k.beneficeEstime),
                k.chantiersEstimes == 0 ? (k.enCours.isEmpty() ? "Aucun chantier en cours" : "Ajoutez un budget aux devis")
                        : k.chantiersEstimes + " chantier" + (k.chantiersEstimes > 1 ? "s" : "") + " avec budget"
                        + (k.enCours.size() > k.chantiersEstimes ? " sur " + k.enCours.size() : "")), UiKit.poids(1));
        duo.setGravity(Gravity.TOP);
        p.contenu.addView(duo, UiKit.pleineLargeur());

        // --- Chantiers en cours
        p.contenu.addView(Blocs.titreSection(a, "Chantiers en cours", "Tout voir", v -> a.allerA(MainActivity.ONGLET_CHANTIERS)), UiKit.pleineLargeur());
        if (k.enCours.isEmpty()) {
            p.contenu.addView(UiKit.text(a, tous.isEmpty() ? "Aucun chantier. Créez le premier depuis l'onglet Chantiers."
                    : "Aucun chantier en cours.", 16, UiKit.ENCRE_2, false));
        } else {
            LinearLayout rang = UiKit.ligne(a, 12);
            rang.setGravity(Gravity.TOP);
            for (Chantier c : k.enCours) rang.addView(carteChantier(c));
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
            hs.addView(rang);
            p.contenu.addView(hs, UiKit.pleineLargeur());
        }

        // --- Activité récente
        List<String[]> act = activite(tous);
        if (!act.isEmpty()) {
            p.contenu.addView(Blocs.titreSection(a, "Activité récente", null, null), UiKit.pleineLargeur());
            for (String[] ligne : act) {
                LinearLayout row = UiKit.ligne(a, 12);
                row.setGravity(Gravity.TOP);
                row.addView(Blocs.avatar(a, ligne[0]));
                LinearLayout txt = UiKit.colonne(a, 2);
                txt.addView(UiKit.text(a, ligne[1], 15, UiKit.ENCRE, false));
                txt.addView(UiKit.text(a, ligne[2], 13, UiKit.ENCRE_2, false));
                row.addView(txt, UiKit.poids(1));
                p.contenu.addView(row, UiKit.pleineLargeur());
            }
        }

        p.barre(a, MainActivity.ONGLET_CARNET);
        return p.racine;
    }

    private View rangeeAlerte(Carnet.Alerte al) {
        boolean pb = al.niveau == Carnet.PROBLEME;
        View past = Blocs.pastille(a, pb ? R.drawable.ic_alerte : R.drawable.ic_check,
                pb ? UiKit.ROUGE_FOND : UiKit.AMBRE_FOND, pb ? UiKit.ROUGE : UiKit.AMBRE, 32);
        LinearLayout row = Blocs.rangee(a, past, al.titre, al.detail, null, al.facture == null,
                v -> a.ouvrir(new FicheScreen(a, al.chantier.id)));
        if (al.facture != null) {
            TextView relancer = UiKit.text(a, "Relancer", 14, UiKit.ENCRE, true);
            relancer.setPadding(dp(12), dp(8), dp(12), dp(8));
            relancer.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(18), dp(2), UiKit.ENCRE)));
            relancer.setContentDescription("Relancer le client");
            relancer.setOnClickListener(v -> relancer(al));
            row.addView(relancer);
        }
        return row;
    }

    /** Prépare un rappel poli (WhatsApp, SMS, mail : au choix du téléphone). */
    private void relancer(Carnet.Alerte al) {
        String libelle = al.facture.get("libelle").isEmpty() ? "la facture" : al.facture.get("libelle").toLowerCase(java.util.Locale.FRANCE);
        String numero = al.facture.get("numero");
        String date = al.facture.get("date_facture");
        String nom = store().entreprise().get("nom");
        String texte = "Bonjour" + (al.chantier.client.isEmpty() ? "" : " " + al.chantier.client) + ",\n\n"
                + "Petit rappel concernant " + libelle + (numero.isEmpty() ? "" : " n° " + numero)
                + " du chantier « " + al.chantier.titre + " »" + (date.isEmpty() ? "" : ", envoyée le " + date)
                + ", d'un montant de " + Money.format(al.montant) + ".\n"
                + "Sauf erreur de notre part, nous n'avons pas encore reçu le règlement.\n\nMerci et bonne journée,\n"
                + (nom == null || nom.isEmpty() ? a.auteur() : nom);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texte);
        if (!al.chantier.telephone.isEmpty()) i.putExtra("address", al.chantier.telephone);
        a.startActivity(Intent.createChooser(i, "Envoyer le rappel"));
    }

    private View carteEncaisser(Carnet k) {
        LinearLayout c = UiKit.colonne(a, 10);
        c.setPadding(dp(20), dp(18), dp(20), dp(18));
        c.setBackground(UiKit.rounded(UiKit.PIERRE, dp(22), 0, 0));
        LinearLayout haut = UiKit.ligne(a, 8);
        haut.addView(UiKit.text(a, "À encaisser", 15, UiKit.ENCRE_2, false), UiKit.poids(1));
        if (k.facturesEnAttente > 0) haut.addView(UiKit.text(a, k.facturesEnAttente + (k.facturesEnAttente > 1 ? " factures" : " facture"), 13, UiKit.ENCRE_2, false));
        c.addView(haut, UiKit.pleineLargeur());
        c.addView(UiKit.titre(a, Money.formatCourt(k.aEncaisser), 42));
        if (k.aEncaisser > 0) {
            LinearLayout barre = UiKit.ligne(a, 2);
            barre.setBackground(UiKit.rounded(Color.parseColor("#D6D2C7"), dp(4), 0, 0));
            long ok = k.aEncaisser - k.enRetard;
            View v1 = new View(a);
            v1.setBackground(UiKit.rounded(UiKit.ENCRE, dp(4), 0, 0));
            barre.addView(v1, new LinearLayout.LayoutParams(0, dp(8), Math.max(0.001f, ok)));
            if (k.enRetard > 0) {
                View v2 = new View(a);
                v2.setBackground(UiKit.rounded(UiKit.AMBRE_VIF, dp(4), 0, 0));
                barre.addView(v2, new LinearLayout.LayoutParams(0, dp(8), k.enRetard));
            }
            c.addView(barre, UiKit.pleineLargeur());
            LinearLayout leg = UiKit.ligne(a, 16);
            leg.addView(UiKit.text(a, "Dans les temps " + Money.formatCourt(ok), 13, UiKit.ENCRE, false));
            if (k.enRetard > 0) leg.addView(UiKit.text(a, "En retard " + Money.formatCourt(k.enRetard), 13, UiKit.AMBRE, true));
            c.addView(leg);
        } else {
            c.addView(UiKit.text(a, "Rien en attente : marquez une ligne « Facturé » pour la suivre ici.", 14, UiKit.ENCRE_2, false));
        }
        return c;
    }

    private View tuileChiffre(String libelle, String valeur, String sous) {
        LinearLayout t = UiKit.colonne(a, 4);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(UiKit.rounded(UiKit.SURFACE, dp(18), 0, 0));
        t.addView(UiKit.text(a, libelle, 14, UiKit.ENCRE_2, false));
        t.addView(UiKit.titre(a, valeur, 26));
        if (!sous.isEmpty()) t.addView(UiKit.text(a, sous, 13, UiKit.ENCRE_2, false));
        return t;
    }

    private View carteChantier(Chantier c) {
        Finance f = a.finance(c);
        LinearLayout carte = UiKit.colonne(a, 0);
        carte.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(20), 0, 0)));
        carte.setClipToOutline(true);
        carte.addView(Blocs.vignette(a, c, 240, 110, 20, true));
        LinearLayout bas = UiKit.colonne(a, 6);
        bas.setPadding(dp(14), dp(12), dp(14), dp(14));
        LinearLayout l1 = UiKit.ligne(a, 6);
        TextView t = UiKit.titre(a, c.titre, 19);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l1.addView(t, UiKit.poids(1));
        if (!c.client.isEmpty()) l1.addView(UiKit.text(a, c.client, 13, UiKit.ENCRE_2, false));
        bas.addView(l1, UiKit.pleineLargeur());
        long conso = f.consommeBp();
        if (conso >= 0) bas.addView(Blocs.barreBudget(a, conso, c.avancementBp, 7));
        LinearLayout l3 = UiKit.ligne(a, 6);
        l3.addView(UiKit.text(a, conso >= 0 ? "Budget " + Finance.pourcent(conso) : "Pas de budget", 13, UiKit.ENCRE_2, false), UiKit.poids(1));
        if (conso >= 0) {
            long b = f.prevision().benefice;
            l3.addView(UiKit.text(a, (b > 0 ? "+" : "") + Money.formatCourt(b), 13, b < 0 ? UiKit.ROUGE : UiKit.ENCRE, true));
        }
        bas.addView(l3, UiKit.pleineLargeur());
        carte.addView(bas, UiKit.pleineLargeur());
        carte.setClickable(true);
        carte.setContentDescription("Chantier " + c.titre);
        carte.setOnClickListener(v -> a.ouvrir(new FicheScreen(a, c.id)));
        carte.setLayoutParams(new LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.WRAP_CONTENT));
        return carte;
    }

    /** Les 6 derniers ajouts sur tous les chantiers : {auteur, phrase, « chantier · quand »}. */
    private List<String[]> activite(List<Chantier> tous) {
        List<Object[]> brut = new ArrayList<>();
        for (Chantier c : tous) {
            for (Chantier.Depense d : c.depenses()) {
                brut.add(new Object[]{d.creeLe, d.auteur, "a ajouté un ticket" + (d.fournisseur.isEmpty() ? "" : " " + d.fournisseur) + " · " + Money.format(d.montantCentimes), c.titre});
            }
            int photos = 0;
            for (Chantier.Element ph : c.photos()) if (photos++ < 1) brut.add(new Object[]{ph.creeLe, ph.auteur, "a ajouté une photo", c.titre});
            for (Chantier.Element t : c.elements(Event.TACHE)) {
                if (Chantier.tacheFaite(t)) brut.add(new Object[]{t.creeLe, t.get("fait_par").isEmpty() ? t.auteur : t.get("fait_par"), "a terminé : " + t.get("texte"), c.titre});
            }
            for (Chantier.Element tr : c.elements(Event.TRAVAUX)) {
                brut.add(new Object[]{tr.creeLe, tr.auteur, "a ajouté des travaux en plus · " + tr.get("description"), c.titre});
            }
        }
        brut.sort((x, y) -> Long.compare((Long) y[0], (Long) x[0]));
        List<String[]> res = new ArrayList<>();
        for (int i = 0; i < Math.min(6, brut.size()); i++) {
            Object[] o = brut.get(i);
            String qui = (String) o[1];
            res.add(new String[]{qui, (qui.isEmpty() ? "Quelqu'un" : qui) + " " + o[2], o[3] + " · " + FicheScreen.quand((Long) o[0])});
        }
        return res;
    }

    private View etatDrive() {
        SyncManager sync = a.sync();
        SyncManager.Etat e = sync.etat();
        boolean ok = e == SyncManager.Etat.A_JOUR;
        boolean pb = e == SyncManager.Etat.ERREUR || e == SyncManager.Etat.RECONNEXION || e == SyncManager.Etat.NON_CONNECTE;
        LinearLayout row = UiKit.ligne(a, 8);
        View point = new View(a);
        point.setBackground(UiKit.rounded(ok ? UiKit.VERT : pb ? UiKit.AMBRE_VIF : UiKit.ENCRE_2, dp(4), 0, 0));
        row.addView(point, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView t = UiKit.text(a, sync.resume(), 14, ok ? UiKit.VERT : pb ? UiKit.AMBRE : UiKit.ENCRE_2, pb);
        row.addView(t, UiKit.poids(1));
        row.setMinimumHeight(dp(36));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (!sync.connecte() || sync.etat() == SyncManager.Etat.RECONNEXION) a.connecterDrive();
            else sync.synchroniser(true);
        });
        return row;
    }
}
