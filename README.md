# Truelle

Appli Android de suivi de chantier : chantiers, statuts, dépenses, notes, et bientôt devis, travaux en plus, photos de tickets avec lecture automatique et PDF de fin de chantier.

## Principes
- **Simple sur le terrain, poussé au calme** : les actions courantes en 3 touches, la gestion détaillée un cran plus loin.
- **Couleurs** : orange = actions, bleu = en cours, vert = terminé / OK, gris = à démarrer, noir = texte et sélection, rouge = alertes.
- **Données** : un journal par chantier, un fichier par saisie, jamais modifié (une correction est un nouveau fichier). Le stockage local reproduit l'arborescence du futur dossier Google Drive.

## Compiler et installer sur Windows (méthode habituelle)
Depuis la racine du dépôt :

1. Double-cliquer sur `BUILD_DEBUG.bat`.
   - Il trouve le SDK Android (`ANDROID_HOME`, `ANDROID_SDK_ROOT` ou `%LOCALAPPDATA%\Android\Sdk`) et écrit `local.properties`.
   - Au premier lancement, le wrapper télécharge Gradle 8.14.3.
   - L'APK est copié à la racine : `Truelle-debug.apk`.
2. Brancher le Pixel en USB (débogage USB activé), puis double-cliquer sur `INSTALL_DEBUG.bat`.
   - Il trouve `adb.exe`, installe avec `adb install -r` et ouvre Truelle sur le téléphone.

Prérequis : JDK 17 et la plateforme Android 36 (`sdkmanager "platforms;android-36" "build-tools;36.0.0"`).

Journal de l'appli pendant un test :
```bat
adb logcat -v time | findstr /i "truelle AndroidRuntime"
```

## Connecter Google Drive (une seule fois)
La synchro passe par le **compte Google de l'entreprise**, le même sur les deux téléphones.

1. **Empreinte de la clé** : double-cliquer sur `GET_SHA1.bat` et copier la ligne `SHA1: AA:BB:…`.
   Pour le téléphone de ton père, l'APK doit être compilé sur ce même PC (même clé).
2. **Console Google Cloud** (console.cloud.google.com), avec le compte qui gère les projets :
   - créer un projet « Truelle » ;
   - *API et services → Bibliothèque* : activer **Google Drive API** ;
   - *Google Auth Platform → Branding* : nom « Truelle », e-mail d'assistance ;
   - *Google Auth Platform → Audience* : type **Externe**, laisser en **Test**, ajouter le compte Google de l'entreprise (et le tien) dans **Utilisateurs test** ;
   - *Google Auth Platform → Clients → Créer un client* : type **Android**, nom du package `com.gregory.truelle`, empreinte SHA-1 de l'étape 1.
3. **Dans l'appli** : toucher « Drive non connecté — toucher pour connecter » sur l'accueil, choisir le compte de l'entreprise et accepter.
   L'appli crée le dossier `Chantiers` dans ce Drive et synchronise à l'ouverture, après chaque saisie et à la demande.

Autorisation demandée : `drive.file` (l'appli ne voit que les fichiers qu'elle a créés elle-même).

## Secours : APK compilé par GitHub
Chaque envoi de code lance aussi la compilation sur GitHub (onglet **Actions** → dernière exécution → **Artifacts** → `truelle-debug`). Utile pour vérifier que tout compile. Attention : cet APK a une autre clé, la connexion Google Drive n'y fonctionne pas.

## Feuille de route
| Lot | Contenu | État |
|---|---|---|
| 1 | Chantiers, statuts, dépenses et notes (saisie manuelle), journal local | fait |
| 2 | Photo de facture (scanner ML Kit, galerie, multi-pages) + lecture du montant, TVA, date, fournisseur | fait |
| 3 | Synchronisation Google Drive entre les deux téléphones (journal sans écrasement) | en test |
| 4 | Gérer le chantier : devis, travaux en plus (signature client), facturation interne, réglages, rentabilité | fait |
| 5 | PDF de fin de chantier : récapitulatif, accords signés, factures fournisseurs en annexe, envoi au client | en test |
| 6 | Catégories de dépenses + PDF par catégorie (récapitulatif client / dossier des factures de dépenses) | en test |
| 7 | Suivi financier (voir `docs/suivi-financier.md`) : chantier Professionnel ou Entre proches, devis en lignes avec TVA au choix (0 / 5,5 / 10 / 20 % / autre), coût prévu par poste, budget prévu / dépensé / engagé / disponible, heures, dépenses à vérifier, acompte / situation / solde, retenue de garantie, bénéfice prévu / réel / estimé, PDF HT / TVA / TTC ; « Entre proches » = même fonctionnement, sans TVA | en test |
| 8 | Nouvelle ergonomie (maquette v3 d'Arthur) : barre Carnet · Chantiers · + · Planning · Outils, Carnet (journée, alertes, à encaisser, bénéfice), fiche chantier unique (couverture, appel, itinéraire, tâches, photos, argent et documents), tâches, photos compressées (1 600 px, miniatures), planning par tâches, calculs (carrelage, peinture, béton, HT ↔ TTC), place libérée sur le téléphone (tout reste sur le Drive), transitions et bandeau « Enregistré ✓ · Annuler », couleurs réservées aux états | en test |
