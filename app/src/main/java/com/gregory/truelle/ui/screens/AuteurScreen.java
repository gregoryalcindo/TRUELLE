package com.gregory.truelle.ui.screens;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

/** Premier lancement : qui utilise ce téléphone ? Sert à savoir qui a saisi quoi dans le journal. */
public final class AuteurScreen extends Screen {

    public AuteurScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.titre(a, "Bienvenue dans Truelle", 34));
        p.entete.addView(UiKit.text(a, "Qui utilise ce téléphone ? Votre prénom apparaîtra à côté de chaque saisie.", 18, UiKit.ENCRE_2, false));

        EditText prenom = UiKit.champ(a, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        prenom.setHint("Votre prénom");
        p.contenu.addView(UiKit.libelle(a, "Prénom", prenom), UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Continuer", v -> {
            String nom = prenom.getText().toString().trim();
            if (nom.isEmpty()) {
                prenom.setError("Indiquez votre prénom");
                return;
            }
            a.setAuteur(nom);
            a.allerA(MainActivity.ONGLET_CARNET);
        }));
        prenom.requestFocus();
        return p.racine;
    }
}
