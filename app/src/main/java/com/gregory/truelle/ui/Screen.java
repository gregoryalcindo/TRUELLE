package com.gregory.truelle.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.LocalStore;

import java.util.Map;

/** Un écran de l'appli. Il est reconstruit à chaque affichage pour montrer des données fraîches. */
public abstract class Screen {

    protected final MainActivity a;

    protected Screen(MainActivity activity) {
        this.a = activity;
    }

    public abstract View build();

    /** Vrai pour un panneau posé par-dessus l'écran précédent (le bouton +). */
    public boolean estSuperpose() {
        return false;
    }

    /** Faux pour les formulaires : on ne veut pas effacer une saisie quand on revient dans l'appli. */
    public boolean rafraichirAuRetour() {
        return true;
    }

    /** Vrai pour les écrans qui affichent l'état de la synchro (redessinés quand il change). */
    public boolean suitLaSynchro() {
        return false;
    }

    protected Context ctx() {
        return a;
    }

    protected LocalStore store() {
        return a.store();
    }

    protected int dp(float v) {
        return UiKit.dp(a, v);
    }

    protected void toast(String message) {
        Toast.makeText(a, message, Toast.LENGTH_SHORT).show();
    }

    /** Enregistre une ligne de journal ; affiche l'erreur au lieu de planter. Retourne vrai si c'est enregistré. */
    protected boolean enregistrer(String chantierId, String type, Map<String, String> data) {
        try {
            store().ajouter(chantierId, Event.create(type, chantierId, a.auteur(), data));
            a.apresEnregistrement();
            return true;
        } catch (Exception e) {
            toast("Enregistrement impossible : " + e.getMessage());
            return false;
        }
    }
}
