package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Écran 4 des maquettes : la dépense.
 * - Après une photo : la facture est affichée et les champs sont pré-remplis, on vérifie et on valide.
 * - Sans photo : saisie à la main, avec un bouton pour photographier la facture.
 * - Avec un identifiant de dépense : correction, ajout ou consultation de la facture, suppression.
 */
public final class DepenseScreen extends Screen {

    private static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);

    private final String chantierId;
    private final String depenseId;

    /** Facture scannée, pas encore rangée dans le chantier. */
    private Scanner.Facture scan;

    // Saisie en cours, gardée si l'écran est redessiné.
    private String montant;
    private String fournisseur;
    private String date;
    private String refacturer;
    private long tvaCentimes = -1;
    /** Catégorie choisie ; null = devinée depuis le fournisseur au moment d'enregistrer. */
    private String categorie;
    /** Taux de TVA choisi (points de base), -1 = déduit de la facture ou taux du chantier. */
    private int tvaBp = -1;
    /** Statut de vérification choisi en modification. */
    private Boolean valide;

    public DepenseScreen(MainActivity a, String chantierId, String depenseId) {
        super(a);
        this.chantierId = chantierId;
        this.depenseId = depenseId;
    }

    /** Nouvelle dépense pré-remplie à partir d'une facture scannée. */
    public static DepenseScreen avecFacture(MainActivity a, String chantierId, Scanner.Facture facture) {
        DepenseScreen s = new DepenseScreen(a, chantierId, null);
        s.scan = facture;
        return s;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Depense existante = (c != null && depenseId != null) ? c.depense(depenseId) : null;
        boolean edition = existante != null;

        if (montant == null) {
            montant = edition ? Money.toInput(existante.montantCentimes) : "";
            fournisseur = edition ? existante.fournisseur : "";
            date = edition && !existante.date.isEmpty() ? existante.date : FORMAT_DATE.format(new Date());
            refacturer = edition ? existante.refacturer : "auto";
            tvaCentimes = edition ? existante.tvaCentimes : -1;
            categorie = edition ? existante.categorie : null;
            tvaBp = edition ? com.gregory.truelle.data.Finance.tvaDepense(existante) : -1;
            valide = edition ? existante.valide : null;
            if (scan != null) completerAvecLecture();
        }

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> annuler()));
        p.entete.addView(UiKit.titre(a, edition ? "Modifier la dépense" : "Nouvelle dépense", 30));
        if (c == null) return p.racine;

        EditText fMontant = UiKit.champ(a, montant, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fMontant.setTypeface(UiKit.TITRE);
        fMontant.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        fMontant.setMinHeight(dp(64));
        fMontant.setHint("0,00 €");
        // Certains claviers numériques n'ont que le point : on accepte aussi la virgule.
        fMontant.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        fMontant.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        EditText fFournisseur = UiKit.champ(a, fournisseur, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fFournisseur.setHint("Ex. : Point P");

        EditText fDate = UiKit.champ(a, date, InputType.TYPE_NULL);
        fDate.setFocusable(false);
        fDate.setOnClickListener(v -> choisirDate(fDate));

        // --- La facture : celle qu'on vient de scanner, celle déjà jointe, ou un bouton pour la photographier.
        File factureJointe = edition ? store().fichier(chantierId, existante.fichier) : null;
        if (scan != null) {
            p.contenu.addView(blocFacture(scan.pdf, true, fMontant, fFournisseur), UiKit.pleineLargeur());
        } else if (factureJointe != null) {
            p.contenu.addView(blocFacture(factureJointe, false, fMontant, fFournisseur), UiKit.pleineLargeur());
        } else if (edition && store().surLeDriveSeulement(chantierId, existante.fichier)) {
            // Facture rangée sur le Drive pour libérer de la place : on la rapatrie à la demande.
            String chemin = existante.fichier;
            p.contenu.addView(UiKit.boutonContour(a, "Facture sur le Drive · la télécharger", v -> {
                toast("Téléchargement…");
                StockageScreen.recupererTout(a, chantierId, java.util.Collections.singletonList(chemin), echecs -> {
                    if (echecs > 0) toast("Téléchargement impossible : vérifiez le réseau et Google Drive.");
                    a.remplacer(this);
                });
            }), UiKit.pleineLargeur());
        } else {
            p.contenu.addView(UiKit.boutonContour(a, edition ? "Ajouter la photo de la facture" : "Prendre la facture en photo",
                    v -> photographier(fMontant, fFournisseur)), UiKit.pleineLargeur());
        }

        p.contenu.addView(UiKit.libelle(a, "Montant TTC", UiKit.euro(a, fMontant)), UiKit.pleineLargeur());
        LinearLayout duo = UiKit.ligne(a, 10);
        duo.setGravity(Gravity.TOP);
        duo.addView(UiKit.libelle(a, "Fournisseur", fFournisseur), UiKit.poids(1.4f));
        duo.addView(UiKit.libelle(a, "Date", fDate), UiKit.poids(1f));
        p.contenu.addView(duo, UiKit.pleineLargeur());
        p.contenu.addView(blocCategorie(c, fMontant, fFournisseur), UiKit.pleineLargeur());

        com.gregory.truelle.data.Finance fin = a.finance(c);
        if (!c.entreProches()) {
            int bp = tauxEffectif();
            long ttc = Math.max(0, Money.parseCentimes(montant));
            long dontTva = ttc - com.gregory.truelle.data.Tva.ht(ttc, bp);
            String aide = (tvaCentimes > 0 ? "TVA lue sur la facture : " + Money.format(tvaCentimes) + ". " : "")
                    + (ttc > 0 ? "Dont TVA " + Money.format(dontTva) + (fin.r.recupereTva ? " (récupérée)." : ", comptée dans le coût.") : "");
            p.contenu.addView(UiKit.blocTva(a, "TVA sur ce ticket", bp, aide, t -> {
                garder(fMontant, fFournisseur);
                tvaBp = t;
                a.remplacer(this);
            }), UiKit.pleineLargeur());
            p.contenu.addView(impactBudget(fin, c, fFournisseur), UiKit.pleineLargeur());
        }
        p.contenu.addView(UiKit.text(a, "Chantier : " + c.titre + (c.client.isEmpty() ? "" : " · " + c.client), 16, UiKit.ENCRE_2, false));

        if (edition) {
            // Niveau « poussé » : visible seulement quand on revient sur une dépense.
            p.contenu.addView(blocVerification(fMontant, fFournisseur), UiKit.pleineLargeur());
            p.contenu.addView(blocRefacturer(c, fMontant, fFournisseur), UiKit.pleineLargeur());
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Valider", v -> valider(existante, fMontant, fFournisseur)));

        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer cette dépense", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer cette dépense ?")
                    .setMessage(Money.format(existante.montantCentimes) + (existante.fournisseur.isEmpty() ? "" : " · " + existante.fournisseur))
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existante.id))) {
                            toast("Dépense supprimée");
                            a.retour();
                        }
                    })
                    .show()));
        } else if (montant.isEmpty()) {
            fMontant.requestFocus();
            if (scan == null) {
                fMontant.post(() -> {
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(fMontant, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
            }
        }
        return p.racine;
    }

    // ------------------------------------------------------------- facture

    /** Aperçu de la facture + message de lecture + boutons « Voir » et « Reprendre ». */
    private View blocFacture(File pdf, boolean nouvelle, EditText fMontant, EditText fFournisseur) {
        LinearLayout bloc = UiKit.carte(a, 12);
        bloc.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));

        LinearLayout haut = UiKit.ligne(a, 14);
        haut.setGravity(Gravity.TOP);
        ImageView vignette = new ImageView(a);
        vignette.setScaleType(ImageView.ScaleType.FIT_START);
        vignette.setBackground(UiKit.rounded(UiKit.NEUTRE, dp(8), dp(1), UiKit.BORDURE));
        vignette.setContentDescription("Aperçu de la facture, toucher pour l'ouvrir");
        Bitmap bmp = Scanner.apercu(pdf, dp(96));
        if (bmp != null) vignette.setImageBitmap(bmp);
        vignette.setOnClickListener(v -> Scanner.ouvrir(a, pdf));
        haut.addView(vignette, new LinearLayout.LayoutParams(dp(96), dp(128)));

        LinearLayout textes = UiKit.colonne(a, 6);
        if (nouvelle) {
            boolean lu = scan.lu.montantTrouve();
            TextView etat = UiKit.text(a, lu ? "✓ Facture lue" : "Facture enregistrée", 18, lu ? UiKit.VERT : UiKit.ENCRE, true);
            textes.addView(etat);
            textes.addView(UiKit.text(a, lu ? "Vérifiez le montant, puis validez." : "Le montant n'a pas pu être lu : saisissez-le.", 16, UiKit.ENCRE_2, false));
        } else {
            textes.addView(UiKit.text(a, "Facture jointe", 18, UiKit.ENCRE, true));
            textes.addView(UiKit.text(a, "Toucher l'aperçu pour l'ouvrir.", 16, UiKit.ENCRE_2, false));
        }
        haut.addView(textes, UiKit.poids(1));
        bloc.addView(haut, UiKit.pleineLargeur());

        LinearLayout boutons = UiKit.ligne(a, 8);
        boutons.addView(UiKit.boutonContour(a, "Voir", v -> Scanner.ouvrir(a, pdf)), UiKit.poids(1));
        boutons.addView(UiKit.boutonContour(a, nouvelle ? "Reprendre" : "Remplacer", v -> photographier(fMontant, fFournisseur)), UiKit.poids(1.4f));
        bloc.addView(boutons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bloc;
    }

    private void photographier(EditText fMontant, EditText fFournisseur) {
        garder(fMontant, fFournisseur);
        a.scannerFacture(new Scanner.Callback() {
            @Override
            public void onFacture(Scanner.Facture facture) {
                supprimerScanTemporaire();
                scan = facture;
                completerAvecLecture();
                a.remplacer(DepenseScreen.this);
            }

            @Override
            public void onAnnule() {
            }

            @Override
            public void onErreur(String message) {
                toast(message);
            }
        });
    }

    /** Remplit les champs encore vides avec ce que la lecture a trouvé (sans écraser une saisie). */
    private void completerAvecLecture() {
        if (scan == null) return;
        if ((montant == null || montant.isEmpty()) && scan.lu.montantTrouve()) montant = Money.toInput(scan.lu.montantCentimes);
        if ((fournisseur == null || fournisseur.isEmpty()) && scan.lu.fournisseur != null) fournisseur = scan.lu.fournisseur;
        if (scan.lu.date != null) date = scan.lu.date;
        if (scan.lu.tvaCentimes > 0) tvaCentimes = scan.lu.tvaCentimes;
        if (scan.lu.tvaCentimes > 0 && scan.lu.montantTrouve()) {
            int t = com.gregory.truelle.data.Tva.deduire(scan.lu.montantCentimes, scan.lu.tvaCentimes);
            if (t >= 0) tvaBp = t;
        }
    }

    private void supprimerScanTemporaire() {
        if (scan != null && scan.pdf != null) {
            //noinspection ResultOfMethodCallIgnored
            scan.pdf.delete();
        }
    }

    private void annuler() {
        supprimerScanTemporaire();
        scan = null;
        a.retour();
    }

    // ------------------------------------------------------------- enregistrement

    private void valider(Chantier.Depense existante, EditText fMontant, EditText fFournisseur) {
        garder(fMontant, fFournisseur);
        long centimes = Money.parseCentimes(montant);
        if (centimes <= 0) {
            fMontant.setError("Indiquez un montant, par ex. 45,90");
            fMontant.requestFocus();
            return;
        }
        String fichier = null;
        if (scan != null) {
            try {
                fichier = store().importerFichier(chantierId, "Dépenses", scan.pdf, com.gregory.truelle.data.NomsFichiers.depense(fournisseur, date, centimes), "pdf");
            } catch (Exception e) {
                toast("La facture n'a pas pu être rangée : " + e.getMessage());
                return;
            }
        }
        boolean ok;
        if (existante == null) {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("montant_centimes", Long.toString(centimes));
            data.put("fournisseur", fournisseur);
            data.put("date", date);
            data.put("refacturer", "auto");
            data.put("categorie", categorie != null ? categorie : com.gregory.truelle.data.Categories.deviner(fournisseur));
            if (fichier != null) data.put("fichier", fichier);
            if (tvaCentimes > 0) data.put("tva_centimes", Long.toString(tvaCentimes));
            data.put("tva_bp", Integer.toString(tauxEffectif()));
            data.put("valide", a.valideDOffice() ? "oui" : "non");
            ok = enregistrer(chantierId, Event.DEPENSE, data);
        } else {
            // On n'enregistre que ce qui a changé.
            Map<String, String> data = new LinkedHashMap<>();
            data.put("cible", existante.id);
            if (centimes != existante.montantCentimes) data.put("montant_centimes", Long.toString(centimes));
            if (!fournisseur.equals(existante.fournisseur)) data.put("fournisseur", fournisseur);
            if (!date.equals(existante.date)) data.put("date", date);
            if (!refacturer.equals(existante.refacturer)) data.put("refacturer", refacturer);
            if (categorie != null && !categorie.equals(existante.categorie)) data.put("categorie", categorie);
            if (fichier != null) data.put("fichier", fichier);
            if (tvaCentimes != existante.tvaCentimes && tvaCentimes > 0) data.put("tva_centimes", Long.toString(tvaCentimes));
            if (tauxEffectif() != com.gregory.truelle.data.Finance.tvaDepense(existante) || existante.tvaBp < 0) data.put("tva_bp", Integer.toString(tauxEffectif()));
            if (valide != null && valide != existante.valide) data.put("valide", valide ? "oui" : "non");
            ok = data.size() == 1 || enregistrer(chantierId, Event.CORRECTION, data);
        }
        if (ok) {
            supprimerScanTemporaire();
            scan = null;
            toast("Dépense enregistrée ✓");
            a.retour();
        }
    }

    private View blocRefacturer(Chantier c, EditText fMontant, EditText fFournisseur) {
        LinearLayout bloc = UiKit.colonne(a, 8);
        String defaut = Chantier.MODE_REEL.equals(c.mode) ? "oui" : "non";
        bloc.addView(UiKit.text(a, "Refacturer au client (chantier « " + (Chantier.MODE_REEL.equals(c.mode) ? "Réel" : "Forfait") + " » : " + defaut + " par défaut)", 16, UiKit.ENCRE_2, false));
        LinearLayout row = UiKit.ligne(a, 8);
        String[][] options = {{"auto", "Selon le chantier"}, {"oui", "Oui"}, {"non", "Non"}};
        for (String[] o : options) {
            row.addView(UiKit.boutonChoix(a, o[1], o[0].equals(refacturer), UiKit.ENCRE, Color.WHITE, v -> {
                garder(fMontant, fFournisseur);
                refacturer = o[0];
                a.remplacer(this);
            }), o[0].equals("auto") ? UiKit.poids(1.6f) : UiKit.poids(1f));
        }
        bloc.addView(row, UiKit.pleineLargeur());
        return bloc;
    }

    /** Catégorie : boutons sur deux lignes ; par défaut, celle devinée depuis le fournisseur. */
    private View blocCategorie(Chantier c, EditText fMontant, EditText fFournisseur) {
        String actuelle = categorie != null ? categorie
                : com.gregory.truelle.data.Categories.deviner(fFournisseur.getText().toString());
        LinearLayout bloc = UiKit.colonne(a, 8);
        bloc.addView(UiKit.text(a, "Catégorie", 16, UiKit.ENCRE_2, false));
        String[][] courts = new String[][]{{"materiel", "Matériel"}, {"location", "Location"}, {"carburant", "Carburant"}, {"repas", "Repas"}, {"sous_traitance", "Sous-trait."}, {"divers", "Divers"}};
        for (int debut = 0; debut < courts.length; debut += 3) {
            LinearLayout row = UiKit.ligne(a, 8);
            for (int i = debut; i < debut + 3; i++) {
                if (i >= courts.length) {
                    row.addView(new View(a), UiKit.poids(1)); // garde la même largeur de bouton
                    continue;
                }
                String[] o = courts[i];
                row.addView(UiKit.boutonChoix(a, o[1], o[0].equals(actuelle), UiKit.ENCRE, Color.WHITE, v -> {
                    garder(fMontant, fFournisseur);
                    categorie = o[0];
                    a.remplacer(this);
                }), UiKit.poids(1));
            }
            bloc.addView(row, UiKit.pleineLargeur());
        }
        return bloc;
    }

    /** Taux de TVA retenu : choisi, sinon déduit de la facture lue, sinon taux du chantier. */
    private int tauxEffectif() {
        if (tvaBp >= 0) return tvaBp;
        long ttc = Money.parseCentimes(montant);
        if (tvaCentimes > 0 && ttc > 0) {
            int t = com.gregory.truelle.data.Tva.deduire(ttc, tvaCentimes);
            if (t >= 0) return t;
        }
        Chantier c = store().charger(chantierId);
        return c == null ? com.gregory.truelle.data.Tva.NORMAL : a.finance(c).tvaDefaut();
    }

    /** Ce qu'il restera dans le budget du poste après cette dépense (comme Spendesk avant d'approuver). */
    private View impactBudget(com.gregory.truelle.data.Finance fin, Chantier c, EditText fFournisseur) {
        String cat = categorie != null ? categorie : com.gregory.truelle.data.Categories.deviner(fFournisseur.getText().toString());
        String poste = com.gregory.truelle.data.Postes.deCategorie(cat);
        long budget = fin.prevuParPoste().get(poste);
        if (budget <= 0) return new View(a);
        long dejaPris = fin.reelParPoste().get(poste);
        Chantier.Depense existante = depenseId == null ? null : c.depense(depenseId);
        if (existante != null) dejaPris -= fin.coutDepense(existante);
        long ttc = Math.max(0, Money.parseCentimes(montant));
        long cout = fin.r.recupereTva ? com.gregory.truelle.data.Tva.ht(ttc, tauxEffectif()) : ttc;
        long reste = budget - dejaPris - cout;
        int couleur = reste < 0 ? UiKit.ROUGE : (budget - reste) * 10 >= budget * 8 ? UiKit.AMBRE : UiKit.ENCRE;
        String txt = "Budget « " + com.gregory.truelle.data.Postes.libelle(poste) + " » : "
                + (reste >= 0 ? "il restera " + Money.format(reste) : "dépassé de " + Money.format(-reste)) + " sur " + Money.format(budget);
        return UiKit.text(a, txt, 16, couleur, true);
    }

    /** À vérifier / Validée (ce que le terrain saisit, le gestionnaire le valide). */
    private View blocVerification(EditText fMontant, EditText fFournisseur) {
        LinearLayout bloc = UiKit.colonne(a, 8);
        bloc.addView(UiKit.text(a, "Vérification", 16, UiKit.ENCRE_2, false));
        bloc.addView(UiKit.choix(a, new String[][]{{"non", "À vérifier"}, {"oui", "Validée"}}, Boolean.TRUE.equals(valide) ? "oui" : "non", choisi -> {
            garder(fMontant, fFournisseur);
            valide = "oui".equals(choisi);
            a.remplacer(this);
        }), UiKit.pleineLargeur());
        return bloc;
    }

    private void garder(EditText fMontant, EditText fFournisseur) {
        montant = fMontant.getText().toString().trim();
        fournisseur = fFournisseur.getText().toString().trim();
    }

    private void choisirDate(EditText fDate) {
        Calendar cal = Calendar.getInstance();
        try {
            Date d = FORMAT_DATE.parse(date);
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {
        }
        new DatePickerDialog(a, (view, y, m, day) -> {
            Calendar choisi = Calendar.getInstance();
            choisi.set(y, m, day);
            date = FORMAT_DATE.format(choisi.getTime());
            fDate.setText(date);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }
}
