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
import com.gregory.truelle.ui.screens.ListeScreen;

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
                cb.onFichier(dest, ext);
            } catch (Exception e) {
                android.widget.Toast.makeText(this, "Import impossible : " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            }
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
                else finish();
            }
        });

        if (auteur().isEmpty()) ouvrir(new AuteurScreen(this));
        else ouvrir(new ListeScreen(this));
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

    public void ouvrir(Screen s) {
        pile.add(s);
        afficher();
    }

    public void retour() {
        if (pile.size() > 1) {
            pile.remove(pile.size() - 1);
            afficher();
        }
    }

    /** Remplace l'écran du dessus (ex. « Nouveau chantier » → fiche du chantier créé). */
    public void remplacer(Screen s) {
        if (!pile.isEmpty()) pile.remove(pile.size() - 1);
        ouvrir(s);
    }

    /** Vide la pile et repart d'un écran racine. */
    public void racine(Screen s) {
        pile.clear();
        ouvrir(s);
    }

    private void afficher() {
        cacherClavier();
        conteneur.removeAllViews();
        Screen haut = pile.get(pile.size() - 1);
        boolean superpose = haut.estSuperpose() && pile.size() > 1;
        // Sous un panneau, les bandes de la barre d'état et de navigation prennent la teinte du voile.
        conteneur.setBackgroundColor(superpose ? UiKit.FOND_VOILE : UiKit.FOND);
        if (superpose) {
            conteneur.addView(pile.get(pile.size() - 2).build());
        }
        conteneur.addView(haut.build());
    }

    private void cacherClavier() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
