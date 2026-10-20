package com.gregory.truelle.ui.screens;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Finance;
import com.gregory.truelle.data.Ligne;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.Postes;
import com.gregory.truelle.data.Tva;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

/**
 * Une ligne de devis : désignation, quantité, unité, prix unitaire (saisi TTC), TVA,
 * et le coût prévu avec l'aide au prix (coût + frais généraux + marge visée).
 * Les modifications sont faites dans la copie de travail du devis, enregistrée avec lui.
 */
public final class LigneScreen extends Screen {

    private final DevisScreen devis;
    private final String chantierId;
    private final int index;
    private final boolean avecTva;
    private final boolean notreDevis;
    private Ligne l;
    private String designation, quantite, prix, cout;

    public LigneScreen(MainActivity a, DevisScreen devis, String chantierId, int index, boolean avecTva, boolean notreDevis) {
        super(a);
        this.devis = devis;
        this.chantierId = chantierId;
        this.index = index;
        this.avecTva = avecTva;
        this.notreDevis = notreDevis;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Finance fin = a.finance(c);
        if (l == null) {
            if (index >= 0) {
                l = devis.lignes.get(index).copie();
            } else {
                l = new Ligne();
                l.tvaBp = avecTva ? fin.tvaDefaut() : 0;
                l.poste = notreDevis ? Postes.MATERIAUX : Postes.SOUS_TRAITANCE;
            }
            if (!avecTva) l.tvaBp = 0;
            designation = l.designation;
            quantite = Ligne.formatQuantite(l.quantiteMilli);
            prix = l.puHtCentimes > 0 ? Money.toInput(Tva.ttc(l.puHtCentimes, l.tvaBp)) : "";
            cout = l.coutPrevuCentimes > 0 ? Money.toInput(l.coutPrevuCentimes) : "";
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Retour au devis", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, index >= 0 ? "Modifier la ligne" : "Nouvelle ligne", 30));

        EditText fDes = UiKit.champ(a, designation, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fDes.setHint("Ex. : Pose de faïence murale");
        EditText fQte = UiKit.champ(a, quantite, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fQte.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        EditText fPrix = UiKit.champMontant(a, prix);
        EditText fCout = UiKit.champMontant(a, cout);
        fCout.setHint("Facultatif");
        Runnable garder = () -> {
            designation = fDes.getText().toString().trim();
            quantite = fQte.getText().toString().trim();
            prix = fPrix.getText().toString().trim();
            cout = fCout.getText().toString().trim();
        };

        p.contenu.addView(UiKit.libelle(a, "Désignation", fDes), UiKit.pleineLargeur());
        LinearLayout qte = UiKit.ligne(a, 10);
        qte.setGravity(Gravity.TOP);
        qte.addView(UiKit.libelle(a, "Quantité", fQte), UiKit.poids(1));
        qte.addView(UiKit.libelle(a, (avecTva ? "Prix unitaire TTC" : "Prix unitaire") + " (€)", UiKit.euro(a, fPrix)), UiKit.poids(1.4f));
        p.contenu.addView(qte, UiKit.pleineLargeur());

        LinearLayout unites = UiKit.colonne(a, 8);
        unites.addView(UiKit.text(a, "Unité", 16, UiKit.ENCRE_2, false));
        String[] u = Ligne.UNITES;
        unites.addView(UiKit.choix(a, new String[][]{{u[0], u[0]}, {u[1], u[1]}, {u[2], u[2]}, {u[3], u[3]}}, l.unite, choisi -> {
            garder.run();
            l.unite = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        unites.addView(UiKit.choix(a, new String[][]{{u[4], u[4]}, {u[5], u[5]}, {u[6], u[6]}, {u[7], u[7]}}, l.unite, choisi -> {
            garder.run();
            l.unite = choisi;
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        p.contenu.addView(unites, UiKit.pleineLargeur());

        if (avecTva) {
            p.contenu.addView(UiKit.blocTva(a, "TVA de la ligne", l.tvaBp, "", t -> {
                garder.run();
                l.tvaBp = t;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
        }

        long q = Ligne.parseQuantite(quantite);
        long puTtc = Money.parseCentimes(prix);
        if (q > 0 && puTtc > 0) {
            p.contenu.addView(UiKit.ligneChiffre(a, "Total de la ligne", Money.format(Tva.arrondi(q * puTtc, 1000)), true), UiKit.pleineLargeur());
        }

        if (notreDevis) {
            p.contenu.addView(UiKit.section(a, "Coût prévu (pour le budget)"));
            p.contenu.addView(UiKit.libelle(a, "Ce que la ligne va nous coûter au total", UiKit.euro(a, fCout)), UiKit.pleineLargeur());
            LinearLayout blocPoste = UiKit.colonne(a, 8);
            blocPoste.addView(UiKit.text(a, "Poste", 16, UiKit.ENCRE_2, false));
            String[][] postes = Postes.COURTS;
            blocPoste.addView(UiKit.choix(a, new String[][]{postes[0], postes[1], postes[2]}, l.poste, choisi -> {
                garder.run();
                l.poste = choisi;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            blocPoste.addView(UiKit.choix(a, new String[][]{postes[3], postes[4]}, l.poste, choisi -> {
                garder.run();
                l.poste = choisi;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            p.contenu.addView(blocPoste, UiKit.pleineLargeur());

            long cc = Money.parseCentimes(cout);
            if (cc > 0 && q > 0 && (fin.r.fraisGenerauxBp > 0 || fin.r.margeViseeBp > 0)) {
                long conseilHt = fin.r.prixConseille(cc);
                long puConseilTtc = Tva.ttc(Tva.arrondi(conseilHt * 1000L, q), l.tvaBp);
                p.contenu.addView(UiKit.text(a, "Prix conseillé : " + Money.format(puConseilTtc) + " / " + l.unite
                        + " (coût + frais généraux " + Tva.libelle(fin.r.fraisGenerauxBp) + " + marge " + Tva.libelle(fin.r.margeViseeBp) + ")", 16, UiKit.ENCRE, true));
                p.contenu.addView(UiKit.boutonContour(a, "Appliquer le prix conseillé", v -> {
                    garder.run();
                    prix = Money.toInput(puConseilTtc);
                    a.remplacer(this);
                }), UiKit.pleineLargeur());
            } else if (cc > 0) {
                p.contenu.addView(UiKit.text(a, "Réglez vos frais généraux et votre marge visée dans les réglages de l'appli pour obtenir un prix conseillé.", 15, UiKit.ENCRE_2, false));
            }
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Valider la ligne", v -> {
            garder.run();
            long qq = Ligne.parseQuantite(quantite);
            long pp = Money.parseCentimes(prix);
            if (designation.isEmpty()) {
                fDes.setError("Décrivez l'ouvrage");
                return;
            }
            if (qq <= 0) {
                fQte.setError("Ex. : 1 ou 12,5");
                return;
            }
            if (pp < 0) {
                fPrix.setError("Prix invalide");
                return;
            }
            l.designation = designation;
            l.quantiteMilli = qq;
            l.puHtCentimes = Tva.ht(pp, l.tvaBp);
            l.coutPrevuCentimes = cout.isEmpty() ? 0 : Math.max(0, Money.parseCentimes(cout));
            if (index >= 0) devis.lignes.set(index, l);
            else devis.lignes.add(l);
            a.retour();
        }));
        if (index >= 0) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer la ligne", v -> {
                devis.lignes.remove(index);
                a.retour();
            }));
        }
        return p.racine;
    }
}
