package com.gregory.truelle;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.gregory.truelle.data.LocalStore;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat ctl = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctl.setAppearanceLightStatusBars(true);
        ctl.setAppearanceLightNavigationBars(true);

        store = new LocalStore(new File(getFilesDir(), "Chantiers"));

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
    }

    public LocalStore store() {
        return store;
    }

    public String auteur() {
        return prefs().getString(KEY_AUTEUR, "");
    }

    public void setAuteur(String nom) {
        prefs().edit().putString(KEY_AUTEUR, nom.trim()).apply();
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
        if (haut.estSuperpose() && pile.size() > 1) {
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
