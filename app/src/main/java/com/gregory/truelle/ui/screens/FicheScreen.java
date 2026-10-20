package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.R;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Heures;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Postes;
import com.gregory.truelle.ui.Images;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fiche du chantier : tout le chantier sur une page (remplace « Gérer le chantier » et ses onglets).
 * Couverture, client, argent, actions rapides, tâches, photos, puis l'argent et les documents.
 * Identique pour un chantier pro ou entre proches (étiquette « Sans TVA »).
 */
public final class FicheScreen extends Screen {

    private final String chantierId;

    public FicheScreen(MainActivity a, String chantierId) {
        super(a);
        this.chantierId = chantierId;
    }

    @Override
    public boolean suitLaSynchro() {
        return true;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        if (c == null) {
            Page p = new Page(a);
            p.entete.addView(UiKit.retour(a, "Retour", v -> a.retour()));
            p.entete.addView(UiKit.titre(a, "Chantier introuvable", 30));
            return p.racine;
        }
        a.noterDernierChantier(c.id);
        Finance f = a.finance(c);

        LinearLayout racine = UiKit.colonne(a, 0);
        racine.setBackgroundColor(UiKit.FOND);
        ScrollView sv = new ScrollView(a);
        sv.setTag(UiKit.TAG_DEFILE);
        LinearLayout page = UiKit.colonne(a, 0);
        sv.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        racine.addView(sv, UiKit.remplirHauteur());

        page.addView(couverture(c)); // garde sa hauteur (200 ou 240 dp)

        LinearLayout corps = UiKit.colonne(a, 14);
        corps.setPadding(dp(20), dp(16), dp(20), dp(28));
        page.addView(corps, UiKit.pleineLargeur());

        View client = client(c);
        if (client != null) corps.addView(client, UiKit.pleineLargeur());
        corps.addView(carteArgent(c, f), UiKit.pleineLargeur());
        corps.addView(actionsRapides(c), UiKit.pleineLargeur());

        // À faire
        List<Chantier.Element> taches = c.taches();
        int faites = c.tachesFaites();
        corps.addView(Blocs.titreSection(a, "À faire", taches.isEmpty() ? null : faites + " sur " + taches.size() + " faites",
                null), UiKit.pleineLargeur());
        Blocs.Groupe g = new Blocs.Groupe(a);
        int montrees = 0;
        for (Chantier.Element t : taches) {
            if (Chantier.tacheFaite(t) || montrees >= 4) continue;
            montrees++;
            g.ajouter(Blocs.rangeeTache(a, t, null, v -> Blocs.basculerTache(a, c.id, t, () -> a.remplacer(this)),
                    v -> a.ouvrir(new TacheScreen(a, c.id, t.id, null))));
        }
        int restantes = taches.size() - faites - montrees;
        g.ajouter(Blocs.rangee(a, Blocs.pastille(a, R.drawable.ic_plus, UiKit.FOND, UiKit.ENCRE, 32),
                "Ajouter une tâche", null, null, false, v -> a.ouvrir(new TacheScreen(a, c.id, null, null))));
        if (restantes > 0 || faites > 0) {
            g.ajouter(Blocs.rangee(a, null, "Toutes les tâches",
                    (restantes > 0 ? restantes + " autre" + (restantes > 1 ? "s" : "") + " à faire · " : "") + faites + " faite" + (faites > 1 ? "s" : ""),
                    null, true, v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.TACHES))));
        }
        corps.addView(g.vue, UiKit.pleineLargeur());

        // Photos
        List<Chantier.Element> photos = c.photos();
        corps.addView(Blocs.titreSection(a, "Photos", photos.isEmpty() ? null : "Avant · Pendant · Après",
                photos.isEmpty() ? null : v -> a.ouvrir(new PhotosScreen(a, c.id))), UiKit.pleineLargeur());
        corps.addView(bandePhotos(c, photos), UiKit.pleineLargeur());

        // Argent et documents
        corps.addView(Blocs.titreSection(a, "Argent et documents", null, null), UiKit.pleineLargeur());
        corps.addView(sections(c, f), UiKit.pleineLargeur());

        return racine;
    }

    // ------------------------------------------------------------- couverture

    private View couverture(Chantier c) {
        FrameLayout cadre = new FrameLayout(a);
        Chantier.Element ph = c.photoCouverture();
        Bitmap b = null;
        if (ph != null) {
            File grande = store().fichier(c.id, ph.get("fichier"));
            b = Images.charger(grande != null ? grande : store().fichier(c.id, ph.get("miniature")), dp(420));
        }
        boolean photo = b != null;
        if (photo) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
            iv.setContentDescription("Photo de couverture du chantier");
            cadre.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View degrade = new View(a);
            degrade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{Color.parseColor("#B31B1F23"), Color.TRANSPARENT}));
            cadre.addView(degrade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), Gravity.BOTTOM));
        } else {
            cadre.setBackgroundColor(UiKit.PIERRE);
        }

        // Boutons ronds en haut : retour, photo, réglages.
        LinearLayout haut = UiKit.ligne(a, 8);
        haut.addView(rond(R.drawable.ic_back, "Retour", v -> a.retour()));
        haut.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        haut.addView(rond(R.drawable.ic_camera, "Ajouter une photo", v -> {
            String moment = c.momentParDefaut();
            a.prendrePhoto(fs -> PhotosScreen.enregistrer(a, c.id, fs, moment, a::rafraichir));
        }));
        haut.addView(rond(R.drawable.ic_reglages, "Réglages du chantier", v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.REGLAGES))));
        FrameLayout.LayoutParams lpHaut = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lpHaut.setMargins(dp(16), dp(12), dp(16), 0);
        cadre.addView(haut, lpHaut);

        LinearLayout bas = UiKit.colonne(a, 8);
        TextView titre = UiKit.titre(a, c.titre, 32);
        titre.setTextColor(photo ? Color.WHITE : UiKit.ENCRE);
        bas.addView(titre);
        LinearLayout puces = UiKit.ligne(a, 8);
        int[] col = UiKit.couleursStatut(c.statut);
        TextView statut = UiKit.chip(a, Chantier.libelleStatut(c.statut) + "  ▾", col[0], col[1]);
        statut.setTextSize(14);
        statut.setMinHeight(dp(36));
        statut.setGravity(Gravity.CENTER_VERTICAL);
        statut.setContentDescription("Statut : " + Chantier.libelleStatut(c.statut) + ", toucher pour changer");
        statut.setOnClickListener(v -> changerStatut(c));
        puces.addView(statut);
        String type = c.entreProches() ? "Sans TVA" : Chantier.MODE_REEL.equals(c.mode) ? "Pro · matériel refacturé" : "Pro · au forfait";
        TextView t = UiKit.chip(a, type, photo ? Color.parseColor("#E6FFFFFF") : UiKit.SURFACE, UiKit.ENCRE);
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.DEFAULT);
        puces.addView(t);
        bas.addView(puces);
        FrameLayout.LayoutParams lpBas = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lpBas.setMargins(dp(20), 0, dp(20), dp(16));
        cadre.addView(bas, lpBas);

        cadre.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(photo ? 240 : 200)));
        return cadre;
    }

    private View rond(int icone, String desc, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(24), 0, 0)));
        f.setElevation(dp(2));
        f.addView(UiKit.icone(a, icone, UiKit.ENCRE, 22), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        f.setClickable(true);
        f.setContentDescription(desc);
        f.setOnClickListener(l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return f;
    }

    private void changerStatut(Chantier c) {
        String[] statuts = {Chantier.A_DEMARRER, Chantier.EN_COURS, Chantier.TERMINE};
        String[] libelles = new String[3];
        int sel = 0;
        for (int i = 0; i < 3; i++) {
            libelles[i] = Chantier.libelleStatut(statuts[i]);
            if (statuts[i].equals(c.statut)) sel = i;
        }
        new AlertDialog.Builder(a).setTitle("Statut du chantier")
                .setSingleChoiceItems(libelles, sel, (d, i) -> {
                    d.dismiss();
                    if (statuts[i].equals(c.statut)) return;
                    if (enregistrer(c.id, Event.STATUT, Collections.singletonMap("valeur", statuts[i]))) {
                        a.confirmer("Statut : " + libelles[i] + " ✓");
                        a.remplacer(this);
                    }
                }).show();
    }

    // ------------------------------------------------------------- client

    private View client(Chantier c) {
        if (c.client.isEmpty() && c.adresse.isEmpty() && c.telephone.isEmpty()) return null;
        LinearLayout row = UiKit.ligne(a, 10);
        LinearLayout txt = UiKit.colonne(a, 2);
        if (!c.client.isEmpty()) txt.addView(UiKit.text(a, c.client, 17, UiKit.ENCRE, true));
        if (!c.adresse.isEmpty()) txt.addView(UiKit.text(a, c.adresse, 15, UiKit.ENCRE_2, false));
        row.addView(txt, UiKit.poids(1));
        if (!c.telephone.isEmpty()) {
            row.addView(rondContour(R.drawable.ic_phone, "Appeler " + c.client, v -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.telephone.replace(" ", ""))));
                } catch (Exception e) {
                    toast("Pas d'application de téléphone");
                }
            }));
        }
        if (!c.adresse.isEmpty()) {
            row.addView(rondContour(R.drawable.ic_route, "Itinéraire vers le chantier", v -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(c.adresse))));
                } catch (Exception e) {
                    toast("Pas d'application de cartes");
                }
            }));
        }
        return row;
    }

    private View rondContour(int icone, String desc, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(24), dp(1), UiKit.BORDURE)));
        f.addView(UiKit.icone(a, icone, UiKit.ENCRE, 20), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        f.setClickable(true);
        f.setContentDescription(desc);
        f.setOnClickListener(l);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return f;
    }

    // ------------------------------------------------------------- argent

    private View carteArgent(Chantier c, Finance f) {
        LinearLayout carte = UiKit.colonne(a, 10);
        carte.setPadding(dp(18), dp(16), dp(18), dp(16));
        carte.setBackground(UiKit.ripple(UiKit.rounded(UiKit.SURFACE, dp(22), 0, 0)));
        Finance.Synthese s = f.prevision();
        long conso = f.consommeBp();

        LinearLayout l1 = UiKit.ligne(a, 8);
        l1.setGravity(Gravity.BOTTOM);
        LinearLayout gauche = UiKit.colonne(a, 2);
        // Sans budget, le bénéfice n'est pas estimable : on montre ce qui est vendu, pas un faux bénéfice.
        boolean estimable = conso >= 0;
        boolean sansTva = f.proches() || f.r.franchise();
        gauche.addView(UiKit.text(a, estimable ? "Bénéfice estimé" : sansTva ? "Montant du chantier" : "Montant du chantier (HT)", 14, UiKit.ENCRE_2, false));
        TextView b = UiKit.titre(a, estimable ? Money.formatCourt(s.benefice) : f.venduHt() > 0 ? Money.formatCourt(f.venduHt()) : "—", 34);
        if (estimable && s.benefice < 0) b.setTextColor(UiKit.ROUGE);
        gauche.addView(b);
        l1.addView(gauche, UiKit.poids(1));
        TextView etat = etat(c, f, s);
        if (etat != null) l1.addView(etat);
        carte.addView(l1, UiKit.pleineLargeur());

        if (conso >= 0) {
            long budget = Finance.somme(f.prevuParPoste());
            long reel = Finance.somme(f.reelParPoste());
            carte.addView(UiKit.ligneChiffre(a, "Budget utilisé", Money.formatCourt(reel) + " sur " + Money.formatCourt(budget), false), UiKit.pleineLargeur());
            carte.addView(Blocs.barreBudget(a, conso, c.avancementBp, 10));
            String leg = "Budget " + Finance.pourcent(conso) + (c.avancementBp >= 0 ? " · travaux faits " + Finance.pourcent(c.avancementBp) : "");
            carte.addView(UiKit.text(a, leg, 13, UiKit.ENCRE_2, false));
        } else {
            long depense = Finance.somme(f.reelParPoste());
            if (depense > 0) carte.addView(UiKit.ligneChiffre(a, "Dépensé à ce jour", Money.formatCourt(depense), false), UiKit.pleineLargeur());
            carte.addView(UiKit.text(a, "Bénéfice pas encore estimable : indiquez le coût prévu sur les lignes du devis signé.", 14, UiKit.ENCRE_2, false));
        }
        carte.setClickable(true);
        carte.setContentDescription("Budget et bénéfice, voir le détail");
        carte.setOnClickListener(v -> a.ouvrir(new RentabiliteScreen(a, c.id)));
        return carte;
    }

    /** Une étiquette d'état : à perte, dépassé, sinon « Dans le budget ». */
    private TextView etat(Chantier c, Finance f, Finance.Synthese s) {
        if (f.venduHt() > 0 && s.benefice < 0) return Blocs.etiquette(a, "À perte", 2);
        for (String[] po : Postes.TOUS) if (f.alerte(po[0]) == 2) return Blocs.etiquette(a, "Dépassé", 2);
        if (f.consommeBp() >= 8000) return Blocs.etiquette(a, "Attention", 1);
        if (f.consommeBp() >= 0) return Blocs.etiquette(a, "✓ Dans le budget", 3);
        return null;
    }

    // ------------------------------------------------------------- actions rapides

    private View actionsRapides(Chantier c) {
        LinearLayout row = UiKit.ligne(a, 8);
        row.addView(action("Ticket", R.drawable.ic_receipt, true, v -> a.scannerFacture(new Scanner.Callback() {
            @Override
            public void onFacture(Scanner.Facture fac) {
                a.ouvrir(DepenseScreen.avecFacture(a, c.id, fac));
            }

            @Override
            public void onAnnule() {
                a.ouvrir(new DepenseScreen(a, c.id, null));
            }

            @Override
            public void onErreur(String message) {
                toast(message);
                a.ouvrir(new DepenseScreen(a, c.id, null));
            }
        })), UiKit.poids(1));
        row.addView(action("Heures", R.drawable.ic_horloge, false, v -> a.ouvrir(new HeuresScreen(a, c.id, null))), UiKit.poids(1));
        row.addView(action("Tâche", R.drawable.ic_tache, false, v -> a.ouvrir(new TacheScreen(a, c.id, null, null))), UiKit.poids(1));
        row.addView(action("Travaux +", R.drawable.ic_plus_box, false, v -> a.ouvrir(new TravauxScreen(a, c.id, null))), UiKit.poids(1));
        return row;
    }

    private View action(String label, int icone, boolean principale, View.OnClickListener l) {
        LinearLayout t = UiKit.colonne(a, 6);
        t.setGravity(Gravity.CENTER);
        t.setMinimumHeight(dp(80));
        t.setBackground(UiKit.ripple(UiKit.rounded(principale ? UiKit.ACTION : UiKit.SURFACE, dp(18), 0, 0)));
        int couleur = principale ? Color.WHITE : UiKit.ENCRE;
        t.addView(UiKit.icone(a, icone, couleur, 24));
        TextView tv = UiKit.text(a, label, 13, couleur, true);
        tv.setGravity(Gravity.CENTER);
        t.addView(tv);
        t.setClickable(true);
        t.setContentDescription(label);
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------- photos

    private View bandePhotos(Chantier c, List<Chantier.Element> photos) {
        LinearLayout row = UiKit.ligne(a, 8);
        int n = Math.min(3, photos.size());
        for (int i = 0; i < n; i++) {
            Chantier.Element ph = photos.get(i);
            FrameLayout f = new FrameLayout(a);
            f.setBackground(UiKit.rounded(UiKit.PIERRE, dp(14), 0, 0));
            f.setClipToOutline(true);
            Bitmap b = Images.charger(store().fichier(c.id, ph.get("miniature")), dp(120));
            if (b != null) {
                ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(b);
                f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            f.setClickable(true);
            f.setContentDescription("Photo " + PhotosScreen.libelleMoment(ph.get("moment")));
            f.setOnClickListener(v -> a.ouvrir(new PhotoScreen(a, c.id, ph.id)));
            row.addView(f, new LinearLayout.LayoutParams(0, dp(84), 1f));
        }
        // Dernière case : « + n » (toutes les photos) ou « Ajouter ».
        TextView plus = UiKit.text(a, photos.size() > 3 ? "+ " + (photos.size() - 3) : photos.isEmpty() ? "+ Ajouter une photo" : "+ Ajouter", 14, UiKit.ENCRE_2, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(UiKit.ripple(UiKit.rounded(Color.TRANSPARENT, dp(14), dp(2), UiKit.BORDURE)));
        plus.setOnClickListener(v -> {
            if (photos.size() > 3) a.ouvrir(new PhotosScreen(a, c.id));
            else {
                String moment = c.momentParDefaut();
                a.prendrePhoto(fs -> PhotosScreen.enregistrer(a, c.id, fs, moment, a::rafraichir));
            }
        });
        row.addView(plus, new LinearLayout.LayoutParams(0, dp(84), photos.isEmpty() ? 4f : 1f));
        return row;
    }

    // ------------------------------------------------------------- argent et documents

    private View sections(Chantier c, Finance f) {
        Blocs.Groupe g = new Blocs.Groupe(a);
        boolean sansTva = f.proches() || f.r.franchise();

        int nousSignes = 0, sousTraitants = 0;
        long devisTtc = 0;
        for (Chantier.Element d : c.elements(Event.DEVIS)) {
            if (Finance.estNotreDevis(d)) {
                if (Finance.signe(d)) {
                    nousSignes++;
                    devisTtc += f.totaux(d).ttc;
                }
            } else sousTraitants++;
        }
        int nDevis = c.elements(Event.DEVIS).size();
        g.ajouter(Blocs.rangee(a, null, "Devis", nDevis == 0 ? "Aucun devis" : nousSignes + " signé" + (nousSignes > 1 ? "s" : "")
                        + (sousTraitants > 0 ? " · " + sousTraitants + " sous-traitant" + (sousTraitants > 1 ? "s" : "") : ""),
                devisTtc > 0 ? Money.formatCourt(devisTtc) : null, true, v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.DEVIS))));

        int nDep = c.depenses().size(), aVerif = f.aVerifier();
        CharSequence sousDep = nDep == 0 ? "Aucun ticket" : nDep + (nDep > 1 ? " tickets" : " ticket");
        if (aVerif > 0) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(sousDep + " · ");
            int debut = sb.length();
            sb.append(aVerif + " à vérifier");
            sb.setSpan(new android.text.style.ForegroundColorSpan(UiKit.AMBRE), debut, sb.length(), 0);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), debut, sb.length(), 0);
            sousDep = sb;
        }
        g.ajouter(Blocs.rangee(a, null, "Dépenses", sousDep, nDep > 0 ? Money.formatCourt(c.totalDepensesCentimes()) : null, true,
                v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.DEPENSES))));

        long cout = 0;
        for (Chantier.Heure h : c.heures()) cout += f.coutHeure(h);
        g.ajouter(Blocs.rangee(a, null, "Main d'œuvre", c.heures().isEmpty() ? "Aucune heure saisie" : Heures.format(f.totalHeuresCentiemes()) + " saisies",
                cout > 0 ? Money.formatCourt(cout) : null, true, v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.HEURES))));

        int acc = 0, att = 0;
        long trav = 0;
        for (Chantier.Element t : c.elements(Event.TRAVAUX)) {
            if (Finance.accepte(t)) {
                acc++;
                trav += f.ttcTravaux(t);
            } else if (Chantier.TRAVAUX_PROPOSE.equals(t.get("statut"))) att++;
        }
        g.ajouter(Blocs.rangee(a, null, "Travaux en plus", acc + att == 0 ? "Aucun" : acc + " accepté" + (acc > 1 ? "s" : "") + (att > 0 ? " · " + att + " en attente" : ""),
                trav > 0 ? Money.formatCourt(trav) : null, true, v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.TRAVAUX))));

        long total = com.gregory.truelle.data.RecapFin.construire(f, false).totalTtc;
        long encaisse = f.encaisse();
        String sousFact = c.elements(Event.FACTURATION).isEmpty() ? "Rien de facturé" :
                "Payé " + Money.formatCourt(encaisse) + " · reste " + Money.formatCourt(Math.max(0, total - encaisse - f.retenues()));
        g.ajouter(Blocs.rangee(a, null, "Factures et paiements", sousFact,
                total > 0 ? Finance.pourcent(Math.min(10000, Math.round(encaisse * 10000.0 / total))) : null, true,
                v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.FACTURATION))));

        g.ajouter(Blocs.rangee(a, null, "Documents PDF", "Récapitulatif client" + (sansTva ? " sans TVA" : "") + " · dossier des factures",
                null, true, v -> a.ouvrir(new FinChantierScreen(a, c.id))));

        int nNotes = 0;
        for (Chantier.Ajout aj : c.derniersAjouts()) if (Event.NOTE.equals(aj.type)) nNotes++;
        g.ajouter(Blocs.rangee(a, null, "Notes", nNotes == 0 ? "Aucune note" : nNotes + (nNotes > 1 ? " notes" : " note"), null, true,
                v -> a.ouvrir(new GererScreen(a, c.id, GererScreen.NOTES))));
        return g.vue;
    }

    /** « Aujourd'hui », « Hier », ou la date. */
    static String quand(long at) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(at);
        boolean memeAnnee = now.get(Calendar.YEAR) == then.get(Calendar.YEAR);
        int diff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR);
        if (memeAnnee && diff == 0) return "Aujourd'hui";
        if (memeAnnee && diff == 1) return "Hier";
        return new SimpleDateFormat(memeAnnee ? "d MMM" : "d MMM yyyy", Locale.FRANCE).format(new Date(at));
    }
}
