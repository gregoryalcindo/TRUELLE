package com.gregory.truelle.sync;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gère la connexion Google et la synchronisation en arrière-plan :
 * à l'ouverture de l'appli, quelques secondes après chaque saisie, et à la demande.
 */
public final class SyncManager {

    public static final String DRIVE_FILE = "https://www.googleapis.com/auth/drive.file";
    private static final String PREFS = "truelle";
    private static final String KEY_CONNECTE = "drive_connecte";
    private static final String KEY_DERNIERE = "drive_derniere_synchro";

    public enum Etat { NON_CONNECTE, EN_COURS, A_JOUR, HORS_LIGNE, RECONNEXION, ERREUR }

    public interface Ecouteur {
        /** Appelé sur le fil principal. recus > 0 : de nouvelles données sont arrivées, il faut redessiner. */
        void onChangement(Etat etat, int recus);
    }

    /** Pour afficher l'écran de choix du compte / consentement Google. */
    public interface Resolution {
        void lancer(PendingIntent intent);
    }

    private static SyncManager instance;

    private final Context app;
    private final File racine;
    private final Handler principal = new Handler(Looper.getMainLooper());
    private final ExecutorService fil = Executors.newSingleThreadExecutor();
    private final Runnable synchroDifferee = () -> synchroniser(false);

    private Etat etat;
    private String message = "";
    private boolean enCours;
    private boolean relancer;
    private Ecouteur ecouteur;

    public static synchronized SyncManager get(Context c, File racine) {
        if (instance == null) instance = new SyncManager(c.getApplicationContext(), racine);
        return instance;
    }

    private SyncManager(Context app, File racine) {
        this.app = app;
        this.racine = racine;
        this.etat = connecte() ? Etat.A_JOUR : Etat.NON_CONNECTE;
    }

    public void setEcouteur(Ecouteur e) {
        this.ecouteur = e;
    }

    public Etat etat() {
        return etat;
    }

    public String message() {
        return message;
    }

    public boolean connecte() {
        return prefs().getBoolean(KEY_CONNECTE, false);
    }

    /** Texte court pour l'écran d'accueil. */
    public String resume() {
        switch (etat) {
            case NON_CONNECTE: return "Drive non connecté — toucher pour connecter";
            case EN_COURS: return "Synchronisation en cours…";
            case HORS_LIGNE: return "Hors ligne — envoi dès que le réseau revient";
            case RECONNEXION: return "Reconnexion Google nécessaire — toucher";
            case ERREUR: return "Erreur Drive — toucher pour réessayer";
            default:
                long t = prefs().getLong(KEY_DERNIERE, 0);
                return t == 0 ? "Drive connecté" : "Synchronisé à " + new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date(t));
        }
    }

    // ------------------------------------------------------------ connexion

    private AuthorizationRequest requete() {
        return AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_FILE)))
                .build();
    }

    /** Connexion interactive : choix du compte Google de l'entreprise et accord. */
    public void connecter(Activity activity, Resolution resolution) {
        Identity.getAuthorizationClient(activity).authorize(requete())
                .addOnSuccessListener(r -> {
                    if (r.hasResolution() && r.getPendingIntent() != null) resolution.lancer(r.getPendingIntent());
                    else connexionReussie(r);
                })
                .addOnFailureListener(e -> changer(Etat.ERREUR, "Connexion Google impossible : " + e.getMessage(), 0));
    }

    /** Retour de l'écran Google (choix du compte, accord). */
    public void resultatConnexion(Activity activity, Intent data) {
        try {
            AuthorizationResult r = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data);
            connexionReussie(r);
        } catch (Exception e) {
            changer(Etat.ERREUR, "Connexion Google annulée ou refusée : " + e.getMessage(), 0);
        }
    }

    private void connexionReussie(AuthorizationResult r) {
        if (r.getAccessToken() == null || r.getAccessToken().isEmpty()) {
            changer(Etat.ERREUR, "Google n'a pas donné d'accès à Drive", 0);
            return;
        }
        prefs().edit().putBoolean(KEY_CONNECTE, true).apply();
        synchroniser(true);
    }

    public void deconnecter() {
        prefs().edit().putBoolean(KEY_CONNECTE, false).apply();
        changer(Etat.NON_CONNECTE, "", 0);
    }

    // ------------------------------------------------------------ synchronisation

    /** Demande une synchro dans quelques secondes (regroupe plusieurs saisies rapprochées). */
    public void demander(long delaiMs) {
        if (!connecte()) return;
        principal.removeCallbacks(synchroDifferee);
        principal.postDelayed(synchroDifferee, delaiMs);
    }

    public void synchroniser(boolean signalerDebut) {
        if (!connecte()) {
            changer(Etat.NON_CONNECTE, "", 0);
            return;
        }
        if (enCours) {
            relancer = true;
            return;
        }
        enCours = true;
        if (signalerDebut || etat != Etat.A_JOUR) changer(Etat.EN_COURS, "", 0);
        obtenirJeton(null, true);
    }

    private void obtenirJeton(String jetonRefuse, boolean reessayer) {
        Runnable demander = () -> Identity.getAuthorizationClient(app).authorize(requete())
                .addOnSuccessListener(r -> {
                    if (r.hasResolution()) {
                        fin(Etat.RECONNEXION, "", 0);
                    } else if (r.getAccessToken() == null || r.getAccessToken().isEmpty()) {
                        fin(Etat.RECONNEXION, "", 0);
                    } else {
                        executer(r.getAccessToken(), reessayer);
                    }
                })
                .addOnFailureListener(e -> fin(estHorsLigne(e) ? Etat.HORS_LIGNE : Etat.ERREUR, String.valueOf(e.getMessage()), 0));
        if (jetonRefuse == null) {
            demander.run();
        } else {
            Identity.getAuthorizationClient(app)
                    .clearToken(ClearTokenRequest.builder().setToken(jetonRefuse).build())
                    .addOnCompleteListener(t -> demander.run());
        }
    }

    private void executer(String jeton, boolean reessayer) {
        fil.execute(() -> {
            try {
                Synchro.Bilan b = Synchro.synchroniser(racine, new DriveDistant(jeton));
                prefs().edit().putLong(KEY_DERNIERE, System.currentTimeMillis()).apply();
                principal.post(() -> fin(Etat.A_JOUR, b.toString(), b.recus));
            } catch (DriveDistant.JetonExpire e) {
                if (reessayer) principal.post(() -> obtenirJeton(jeton, false));
                else principal.post(() -> fin(Etat.RECONNEXION, "", 0));
            } catch (Exception e) {
                Etat et = estHorsLigne(e) ? Etat.HORS_LIGNE : Etat.ERREUR;
                principal.post(() -> fin(et, String.valueOf(e.getMessage()), 0));
            }
        });
    }

    private void fin(Etat e, String msg, int recus) {
        enCours = false;
        changer(e, msg, recus);
        if (relancer) {
            relancer = false;
            demander(1000);
        }
    }

    private static boolean estHorsLigne(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof UnknownHostException || t instanceof SocketTimeoutException || t instanceof java.net.ConnectException) return true;
            if (t instanceof IOException && String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT).contains("network")) return true;
            t = t.getCause();
        }
        return false;
    }

    private void changer(Etat e, String msg, int recus) {
        etat = e;
        message = msg == null ? "" : msg;
        if (ecouteur != null) ecouteur.onChangement(e, recus);
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
