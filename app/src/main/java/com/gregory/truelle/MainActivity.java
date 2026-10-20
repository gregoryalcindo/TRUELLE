package com.gregory.truelle;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.gregory.truelle.data.LocalStore;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.gregory.truelle.sync.SyncManager;
import com.gregory.truelle.ui.Scanner;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;
import com.gregory.truelle.ui.screens.AuteurScreen;
import com.gregory.truelle.ui.screens.CarnetScreen;
import com.gregory.truelle.ui.screens.ChantiersScreen;
import com.gregory.truelle.ui.screens.OutilsScreen;
import com.gregory.truelle.ui.screens.PlanningScreen;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Activité unique : elle empile des écrans et gère le retour arrière. */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "truelle";
    private static final String KEY_AUTEUR = "auteur";

    private final List<Screen> pile = new ArrayList<>();
    private FrameLayout conteneur;
    private LocalStore store;
    private ActivityResultLauncher<IntentSenderRequest> lanceurScan;
    private Scanner.Callback scanEnCours;
    private ActivityResultLauncher<String[]> lanceurFichier;
    private ActivityResultLauncher<IntentSenderRequest> lanceurGoogle;
    private SyncManager sync;
    private FichierCallback fichierEnCours;
    private ActivityResultLauncher<android.net.Uri> lanceurPhoto;
    private ActivityResultLauncher<String> lanceurGalerie;
    private PhotosCallback photosEnCours;
    private File photoEnCours;

    /** Photos prises ou choisies, copiées dans le cache (pas encore réduites). */
    public interface PhotosCallback {
        void onPhotos(List<File> photos);
    }

    /** Fichier choisi dans le téléphone (PDF reçu par mail, photo…), copié dans le cache. */
    public interface FichierCallback {
        void onFichier(File fichier, String extension);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctl = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctl.setAppearanceLightStatusBars(true);
        ctl.setAppearanceLightNavigationBars(true);

        store = new LocalStore(new File(getFilesDir(), "Chantiers"));

        // Retour du scanner de documents (doit être déclaré dès la création de l'activité).
        lanceurScan = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), res -> {
            Scanner.Callback cb = scanEnCours;
            scanEnCours = null;
            if (cb == null) return;
            if (res.getResultCode() != RESULT_OK || res.getData() == null) {
                cb.onAnnule();
                return;
            }
            GmsDocumentScanningResult r = GmsDocumentScanningResult.fromActivityResultIntent(res.getData());
            if (r == null) cb.onErreur("Le scan n'a pas pu être lu");
            else Scanner.traiter(this, r, cb);
        });

        // Synchronisation Google Drive.
        sync = SyncManager.get(this, store.root());
        sync.setEcouteur((etat, recus) -> {
            if (pile.isEmpty()) return;
            Screen haut = pile.get(pile.size() - 1);
            if (haut.rafraichirAuRetour() && (recus > 0 || haut.suitLaSynchro())) afficher();
            if (etat == SyncManager.Etat.ERREUR && !sync.message().isEmpty() && haut.suitLaSynchro()) {
                android.widget.Toast.makeText(this, sync.message(), android.widget.Toast.LENGTH_LONG).show();
            }
        });
        lanceurGoogle = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), res -> {
            if (res.getResultCode() == RESULT_OK && res.getData() != null) sync.resultatConnexion(this, res.getData());
            else android.widget.Toast.makeText(this, "Connexion Google annulée", android.widget.Toast.LENGTH_SHORT).show();
        });

        // Choix d'un fichier existant (devis PDF reçu d'un sous-traitant, par exemple).
        lanceurFichier = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            FichierCallback cb = fichierEnCours;
            fichierEnCours = null;
            if (cb == null || uri == null) return;
            try {
                String type = getContentResolver().getType(uri);
                String ext = type != null && type.contains("pdf") ? "pdf" : type != null && type.contains("png") ? "png" : type != null && type.startsWith("image") ? "jpg" : "pdf";
                File dest = new File(getCacheDir(), "import-" + System.currentTimeMillis() + "." + ext);
                try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                     java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                    if (in == null) throw new java.io.IOException("fichier illisible");
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                // Une image importée est réduite comme une photo ; un PDF est gardé tel quel.
                File finale = com.gregory.truelle.ui.Images.reduireSiImage(this, dest, ext);
                cb.onFichier(finale, finale == dest ? ext : "jpg");
            } catch (Exception e) {
                android.widget.Toast.makeText(this, "Import impossible : " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            }
        });

        // Photo du chantier : l'appareil photo écrit directement dans le cache de l'appli.
        lanceurPhoto = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            PhotosCallback cb = photosEnCours;
            File f = photoEnCours;
            photosEnCours = null;
            photoEnCours = null;
            if (cb != null && Boolean.TRUE.equals(ok) && f != null && f.length() > 0) cb.onPhotos(java.util.Collections.singletonList(f));
        });
        lanceurGalerie = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            PhotosCallback cb = photosEnCours;
            photosEnCours = null;
            if (cb == null || uris == null || uris.isEmpty()) return;
            List<File> copies = new ArrayList<>();
            for (android.net.Uri u : uris) {
                File dest = new File(getCacheDir(), "galerie-" + System.nanoTime() + ".jpg");
                try (java.io.InputStream in = getContentResolver().openInputStream(u);
                     java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                    if (in == null) continue;
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    copies.add(dest);
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, "Une photo n'a pas pu être lue", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            if (!copies.isEmpty()) cb.onPhotos(copies);
        });

        conteneur = new FrameLayout(this);
        conteneur.setBackgroundColor(UiKit.FOND);
        // Laisse la place à la barre d'état, à la barre de navigation et au clavier.
        ViewCompat.setOnApplyWindowInsetsListener(conteneur, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        setContentView(conteneur);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pile.size() > 1) retour();
                else if (!(pile.get(0) instanceof CarnetScreen)) allerA(ONGLET_CARNET);
                else finish();
            }
        });

        if (auteur().isEmpty()) ouvrir(new AuteurScreen(this));
        else ouvrir(new CarnetScreen(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Au retour dans l'appli, on rafraîchit les listes, mais jamais un formulaire en cours de saisie.
        if (!pile.isEmpty() && pile.get(pile.size() - 1).rafraichirAuRetour()) afficher();
        sync.demander(500);
    }

    public LocalStore store() {
        return store;
    }

    /** Réglages financiers de l'entreprise (TVA, taux horaire, frais généraux…). */
    public com.gregory.truelle.data.Reglages reglages() {
        return com.gregory.truelle.data.Reglages.lire(store.entreprise());
    }

    public com.gregory.truelle.data.Finance finance(com.gregory.truelle.data.Chantier c) {
        return new com.gregory.truelle.data.Finance(c, reglages());
    }

    /** Sur ce téléphone, les dépenses saisies sont-elles validées d'office ? (non par défaut) */
    public boolean valideDOffice() {
        return prefs().getBoolean("valide_d_office", false);
    }

    public void setValideDOffice(boolean v) {
        prefs().edit().putBoolean("valide_d_office", v).apply();
    }

    /** Dernier chantier utilisé sur ce téléphone (proposé d'office dans le bouton +). */
    public String dernierChantier() {
        return prefs().getString("dernier_chantier", "");
    }

    public void noterDernierChantier(String id) {
        if (id != null) prefs().edit().putString("dernier_chantier", id).apply();
    }

        public String auteur() {
        return prefs().getString(KEY_AUTEUR, "");
    }

    public void setAuteur(String nom) {
        prefs().edit().putString(KEY_AUTEUR, nom.trim()).apply();
    }

    /** Ouvre le scanner de factures ; le résultat arrive dans le callback. */
    public void scannerFacture(Scanner.Callback cb) {
        scanEnCours = cb;
        Scanner.lancer(this, lanceurScan, new Scanner.Callback() {
            @Override
            public void onFacture(Scanner.Facture facture) {
                cb.onFacture(facture);
            }

            @Override
            public void onAnnule() {
                scanEnCours = null;
                cb.onAnnule();
            }

            @Override
            public void onErreur(String message) {
                scanEnCours = null;
                cb.onErreur(message);
            }
        });
    }

    /** Ouvre l'appareil photo ; la photo arrive dans le callback. */
    public void prendrePhoto(PhotosCallback cb) {
        try {
            photoEnCours = new File(getCacheDir(), "appareil-" + System.currentTimeMillis() + ".jpg");
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fichiers", photoEnCours);
            photosEnCours = cb;
            lanceurPhoto.launch(uri);
        } catch (Exception e) {
            photosEnCours = null;
            android.widget.Toast.makeText(this, "Appareil photo indisponible : " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Choisit une ou plusieurs photos dans la galerie. */
    public void choisirPhotos(PhotosCallback cb) {
        photosEnCours = cb;
        lanceurGalerie.launch("image/*");
    }

    public SyncManager sync() {
        return sync;
    }

    /** Connexion au compte Google de l'entreprise (écran Google de choix du compte). */
    public void connecterDrive() {
        sync.connecter(this, pi -> lanceurGoogle.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build()));
    }

    /** À appeler après chaque enregistrement : la synchro part quelques secondes plus tard. */
    public void apresEnregistrement() {
        sync.demander(4000);
    }

    /** Ouvre le sélecteur de fichiers du téléphone (PDF et images). */
    public void choisirFichier(FichierCallback cb) {
        fichierEnCours = cb;
        lanceurFichier.launch(new String[]{"application/pdf", "image/*"});
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // ------------------------------------------------------------- navigation

    /** Sens de la transition : la page arrive de la droite (on avance) ou de la gauche (on revient). */
    private static final int AVANCE = 1, RECULE = -1, SUR_PLACE = 0, FONDU = 2;
    private static final long DUREE = 280;

    /** L'écran actuellement affiché (pour mémoriser où on en était dans la page). */
    private Screen affiche;

    public void ouvrir(Screen s) {
        pile.add(s);
        afficher(AVANCE);
    }

    public void retour() {
        if (pile.size() > 1) {
            pile.remove(pile.size() - 1);
            afficher(RECULE);
        }
    }

    /** Remplace l'écran du dessus (ex. « Nouveau chantier » → fiche du chantier créé, ou simple rafraîchissement). */
    public void remplacer(Screen s) {
        boolean memeEcran = !pile.isEmpty() && pile.get(pile.size() - 1) == s;
        if (!pile.isEmpty()) pile.remove(pile.size() - 1);
        pile.add(s);
        afficher(memeEcran ? SUR_PLACE : FONDU);
    }

    /** Vide la pile et repart d'un écran racine. */
    public void racine(Screen s) {
        pile.clear();
        pile.add(s);
        afficher(FONDU);
    }

    /** Onglets de la barre du bas. */
    public static final String ONGLET_CARNET = "carnet", ONGLET_CHANTIERS = "chantiers", ONGLET_PLANNING = "planning", ONGLET_OUTILS = "outils";

    public void allerA(String onglet) {
        Screen s;
        switch (onglet) {
            case ONGLET_CHANTIERS: s = new ChantiersScreen(this); break;
            case ONGLET_PLANNING: s = new PlanningScreen(this); break;
            case ONGLET_OUTILS: s = new OutilsScreen(this); break;
            default: s = new CarnetScreen(this);
        }
        racine(s);
    }

    /** Redessine l'écran du dessus (après un enregistrement en arrière-plan). */
    public void rafraichir() {
        if (!pile.isEmpty()) afficher(SUR_PLACE);
    }

    /** Rafraîchit l'écran du dessus sans transition (synchro, retour dans l'appli). */
    private void afficher() {
        afficher(SUR_PLACE);
    }

    private void afficher(int sens) {
        cacherClavier();
        // Mémorise où l'on en était dans la page qu'on quitte (retrouvée au retour).
        if (affiche != null && conteneur.getChildCount() > 0) {
            View defile = conteneur.getChildAt(conteneur.getChildCount() - 1).findViewWithTag(UiKit.TAG_DEFILE);
            if (defile != null) affiche.defilement = defile.getScrollY();
        }
        conteneur.removeAllViews();
        Screen haut = pile.get(pile.size() - 1);
        boolean superpose = haut.estSuperpose() && pile.size() > 1;
        // Sous un panneau, les bandes de la barre d'état et de navigation prennent la teinte du voile.
        conteneur.setBackgroundColor(superpose ? UiKit.FOND_VOILE : UiKit.FOND);
        if (superpose) {
            conteneur.addView(pile.get(pile.size() - 2).build());
        }
        View v = haut.build();
        conteneur.addView(v);
        affiche = haut;
        View defile = v.findViewWithTag(UiKit.TAG_DEFILE);
        if (defile != null && haut.defilement > 0) {
            int y = haut.defilement;
            defile.post(() -> defile.scrollTo(0, y));
        }
        animer(v, superpose ? (sens == AVANCE ? 3 : SUR_PLACE) : sens);
        if (bandeau != null) conteneur.addView(bandeau);
    }

    private void animer(View v, int sens) {
        if (sens == SUR_PLACE || !animationsActives()) return;
        android.view.animation.DecelerateInterpolator dec = new android.view.animation.DecelerateInterpolator(2f);
        if (sens == 3) {
            // Panneau du bouton + : le voile apparaît, le panneau monte du bas.
            View panneau = v.findViewWithTag(UiKit.TAG_PANNEAU);
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(DUREE - 80).start();
            if (panneau != null) {
                panneau.setTranslationY(UiKit.dp(this, 420));
                panneau.animate().translationY(0).setDuration(DUREE).setInterpolator(dec).start();
            }
            return;
        }
        if (sens == FONDU) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(180).start();
            return;
        }
        float decalage = UiKit.dp(this, 72) * (sens == AVANCE ? 1 : -1);
        v.setTranslationX(decalage);
        v.setAlpha(0.4f);
        v.animate().translationX(0).alpha(1f).setDuration(DUREE).setInterpolator(dec).start();
    }

    /** Respecte le réglage « supprimer les animations » d'Android. */
    private boolean animationsActives() {
        try {
            return android.provider.Settings.Global.getFloat(getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception e) {
            return true;
        }
    }

    // ------------------------------------------------------------- confirmation « Enregistré ✓ · Annuler »

    private View bandeau;
    private final android.os.Handler minuteur = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable cacherBandeau = this::retirerBandeau;
    /** Dernier ajout annulable : {chantierId, eventId, heure}. */
    private String[] annulable;

    /** Note le dernier élément créé, pour proposer « Annuler » dans la confirmation qui suit. */
    public void noterAnnulable(String chantierId, String eventId) {
        annulable = new String[]{chantierId, eventId, Long.toString(System.currentTimeMillis())};
    }

    /** Petite vibration + bandeau en bas pendant 5 s, avec « Annuler » si on vient de créer quelque chose. */
    public void confirmer(String message) {
        String[] an = annulable;
        annulable = null;
        boolean peutAnnuler = an != null && System.currentTimeMillis() - Long.parseLong(an[2]) < 4000;
        retirerBandeau();
        android.widget.LinearLayout b = UiKit.ligne(this, 12);
        b.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 10), UiKit.dp(this, 8), UiKit.dp(this, 10));
        b.setBackground(UiKit.rounded(UiKit.ENCRE, UiKit.dp(this, 14), 0, 0));
        b.setElevation(UiKit.dp(this, 6));
        b.addView(UiKit.text(this, message, 16, android.graphics.Color.WHITE, true), UiKit.poids(1));
        if (peutAnnuler) {
            android.widget.TextView annuler = UiKit.text(this, "Annuler", 16, android.graphics.Color.WHITE, true);
            annuler.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 10), UiKit.dp(this, 14), UiKit.dp(this, 10));
            annuler.setPaintFlags(annuler.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            annuler.setOnClickListener(x -> {
                retirerBandeau();
                try {
                    java.util.Map<String, String> data = new java.util.LinkedHashMap<>();
                    data.put("cible", an[1]);
                    store.ajouter(an[0], com.gregory.truelle.data.Event.create(com.gregory.truelle.data.Event.SUPPRESSION, an[0], auteur(), data));
                    apresEnregistrement();
                    android.widget.Toast.makeText(this, "Annulé", android.widget.Toast.LENGTH_SHORT).show();
                    afficher();
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, "Annulation impossible : " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            });
            b.addView(annuler);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        int m = UiKit.dp(this, 16);
        lp.setMargins(m, 0, m, UiKit.dp(this, 104));
        b.setLayoutParams(lp);
        bandeau = b;
        conteneur.addView(b);
        b.setAlpha(0f);
        b.setTranslationY(UiKit.dp(this, 20));
        b.animate().alpha(1f).translationY(0).setDuration(200).start();
        conteneur.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        minuteur.removeCallbacks(cacherBandeau);
        minuteur.postDelayed(cacherBandeau, 5000);
    }

    private void retirerBandeau() {
        if (bandeau != null) {
            conteneur.removeView(bandeau);
            bandeau = null;
        }
    }

    private void cacherClavier() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
