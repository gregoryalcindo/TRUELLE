# Truelle

Appli Android de suivi de chantier : chantiers, statuts, dépenses, notes, et bientôt devis, travaux en plus, photos de tickets avec lecture automatique et PDF de fin de chantier.

## Principes
- **Simple sur le terrain, poussé au calme** : les actions courantes en 3 touches, la gestion détaillée un cran plus loin.
- **Couleurs** : orange = actions, bleu = en cours, vert = terminé / OK, gris = à démarrer, noir = texte et sélection, rouge = alertes.
- **Données** : un journal par chantier, un fichier par saisie, jamais modifié (une correction est un nouveau fichier). Le stockage local reproduit l'arborescence du futur dossier Google Drive.

## Récupérer l'APK
Chaque envoi de code lance la compilation sur GitHub (onglet **Actions** → dernière exécution → **Artifacts** → `truelle-debug`).

## Feuille de route
| Lot | Contenu | État |
|---|---|---|
| 1 | Chantiers, statuts, dépenses et notes (saisie manuelle), journal local | en cours |
| 2 | Photo du ticket (scanner ML Kit) + lecture automatique du montant | à faire |
| 3 | Synchronisation Google Drive entre les deux téléphones | à faire |
| 4 | Gérer le chantier : devis, travaux en plus, réglages, rentabilité | à faire |
| 5 | PDF de fin de chantier | à faire |
