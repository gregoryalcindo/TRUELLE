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

## Secours : APK compilé par GitHub
Chaque envoi de code lance aussi la compilation sur GitHub (onglet **Actions** → dernière exécution → **Artifacts** → `truelle-debug`). Utile pour vérifier que tout compile, ou pour installer sans PC.

## Feuille de route
| Lot | Contenu | État |
|---|---|---|
| 1 | Chantiers, statuts, dépenses et notes (saisie manuelle), journal local | fait |
| 2 | Photo de facture (scanner ML Kit, galerie, multi-pages) + lecture du montant, TVA, date, fournisseur | fait |
| 3 | Synchronisation Google Drive entre les deux téléphones | à faire |
| 4 | Gérer le chantier : devis, travaux en plus (signature client), facturation interne, réglages, rentabilité | en test |
| 5 | PDF de fin de chantier | à faire |
