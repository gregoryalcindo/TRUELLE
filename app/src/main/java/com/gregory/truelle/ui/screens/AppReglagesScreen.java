package com.gregory.truelle.ui.screens;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.sync.SyncManager;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.LinkedHashMap;
import java.util.Map;

/** Réglages de l'appli : l'entreprise (en-tête du PDF client), le prénom de l'utilisateur, Google Drive. */
public final class AppReglagesScreen extends Screen {

    private static final String[][] CHAMPS = {
            {"nom", "Nom de l'entreprise", "Ex. : Alcindo Bâtiment"},
            {"adresse", "Adresse", "Rue, code postal, ville"},
            {"telephone", "Téléphone", ""},
            {"email", "E-mail", ""},
            {"siret", "SIRET", ""},
    };

    public AppReglagesScreen(MainActivity a) {
        super(a);
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Mes chantiers", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Réglages de l'appli", 30));

        // --- Google Drive
        SyncManager sync = a.sync();
        LinearLayout drive = UiKit.carte(a, 10);
        drive.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        drive.addView(UiKit.titre(a, "Google Drive", 20));
        drive.addView(UiKit.text(a, sync.connecte()
                ? "Connecté. Les chantiers, factures et signatures sont copiés dans le dossier « Chantiers » du Drive, et partagés avec l'autre téléphone."
                : "Non connecté : les données restent sur ce téléphone. Connectez le compte Google de l'entreprise, le même sur les deux téléphones.", 16, UiKit.ENCRE_2, false));
        if (sync.connecte()) {
            drive.addView(UiKit.text(a, sync.resume(), 16, UiKit.ENCRE, true));
            LinearLayout boutons = UiKit.ligne(a, 8);
            boutons.addView(UiKit.boutonContour(a, "Synchroniser", v -> {
                sync.synchroniser(true);
                toast("Synchronisation lancée");
            }), UiKit.poids(1));
            boutons.addView(UiKit.boutonContour(a, "Déconnecter", v -> {
                sync.deconnecter();
                a.remplacer(this);
            }), UiKit.poids(1));
            drive.addView(boutons, UiKit.pleineLargeur());
        } else {
            drive.addView(UiKit.boutonContour(a, "Connecter Google Drive", v -> {
                a.retour();
                a.connecterDrive();
            }), UiKit.pleineLargeur());
        }
        p.contenu.addView(drive, UiKit.pleineLargeur());

        // --- Utilisateur de ce téléphone
        EditText prenom = UiKit.champ(a, a.auteur(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        p.contenu.addView(UiKit.libelle(a, "Prénom de la personne qui utilise ce téléphone", prenom), UiKit.pleineLargeur());

        // --- Entreprise (en-tête du PDF de fin de chantier)
        p.contenu.addView(UiKit.section(a, "Entreprise"));
        p.contenu.addView(UiKit.text(a, "Ces informations apparaissent en haut du PDF de fin de chantier.", 15, UiKit.ENCRE_2, false));
        Map<String, String> infos = store().entreprise();
        Map<String, EditText> champs = new LinkedHashMap<>();
        for (String[] ch : CHAMPS) {
            int type = "email".equals(ch[0]) ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    : "telephone".equals(ch[0]) ? InputType.TYPE_CLASS_PHONE
                    : "siret".equals(ch[0]) ? InputType.TYPE_CLASS_NUMBER
                    : "adresse".equals(ch[0]) ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
            String valeur = infos.get(ch[0]);
            EditText e = UiKit.champ(a, valeur == null ? "" : valeur, type);
            if ("adresse".equals(ch[0])) {
                e.setMinLines(2);
                e.setGravity(Gravity.TOP | Gravity.START);
            }
            if (!ch[2].isEmpty()) e.setHint(ch[2]);
            champs.put(ch[0], e);
            p.contenu.addView(UiKit.libelle(a, ch[1], e), UiKit.pleineLargeur());
        }

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            String nom = prenom.getText().toString().trim();
            if (!nom.isEmpty()) a.setAuteur(nom);
            Map<String, String> data = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> e : champs.entrySet()) {
                String nouvelle = e.getValue().getText().toString().trim();
                String ancienne = infos.get(e.getKey());
                if (!nouvelle.equals(ancienne == null ? "" : ancienne)) data.put(e.getKey(), nouvelle);
            }
            if (!data.isEmpty()) {
                try {
                    store().ajouterApp(Event.create(Event.ENTREPRISE, null, a.auteur(), data));
                    a.apresEnregistrement();
                } catch (Exception ex) {
                    toast("Enregistrement impossible : " + ex.getMessage());
                    return;
                }
            }
            toast("Réglages enregistrés ✓");
            a.retour();
        }));
        return p.racine;
    }
}
