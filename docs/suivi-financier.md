# Truelle — Architecture du suivi financier

Préparée par Karim (règles comptables et fiscales) et Claude (architecture), appliquée par Bafodé.
Sources : CGI dans le corpus Black Ops (copie du 2026-10-08), loi n° 71-584 du 16 juillet 1971, méthode du déboursé sec (Obat).

## 1. Principes

1. **Tout se saisit et s'affiche en TTC**, comme on paie. Le taux de TVA (0 à x %) est choisi sur chaque ligne, mais seulement pour information : on n'a pas besoin de raisonner en HT.
2. **Coûts en TTC par défaut** (réglage « TVA récupérée sur les achats : non »). Si un jour l'entreprise récupère la TVA, un seul réglage passe les coûts en HT.
3. **Ventes** : réglage « TVA facturée aux clients : oui / non (franchise, art. 293 B CGI) ». Quand la TVA est facturée, elle est due à l'État : le bénéfice se calcule sur le montant client moins la TVA, affichée en clair (« dont TVA à reverser »).
4. **Quatre niveaux d'argent, jamais mélangés :**

| Niveau | Question | D'où vient le chiffre |
|---|---|---|
| **Chiffré** (prévu) | Combien on a prévu de vendre et de dépenser ? | Lignes des devis (prix + coût prévu) |
| **Validé** (vendu) | Qu'est-ce que le client a accepté ? | Devis signés + travaux en plus acceptés |
| **Engagé / dépensé** | Combien ça coûte déjà, payé ou promis ? | Dépenses + heures + devis de sous-traitants acceptés |
| **Facturé / encaissé** | Combien on a demandé, combien on a reçu ? | Suivi de facturation |

5. **Pilotage par budget, comme Spendesk et les outils pros de contrôle des coûts** : pour chaque poste, **Budget → Dépensé → Engagé → Disponible**, avec une alerte orange à 80 % et rouge au-delà de 100 %. Avant de valider une dépense, l'appli montre l'impact sur le budget. On y ajoute une **prévision de fin de chantier** et l'**avancement** déclaré (% des travaux faits), à comparer au % du budget consommé.

## 2. Rappels de TVA (vérifiés par Karim)

| Taux | Quand | Texte |
|---|---|---|
| 20 % | Taux normal : neuf, extension de plus de 10 % de la surface, locaux non habitation, nettoyage, espaces verts, chaudière fossile | CGI art. 278, 279-0 bis 2 et 2 bis |
| 10 % | Amélioration, transformation, aménagement, entretien d'un logement achevé depuis plus de 2 ans | CGI art. 279-0 bis 1 |
| 5,5 % | Rénovation énergétique (isolation, chauffage, ventilation, eau chaude) d'un logement de plus de 2 ans | CGI art. 278-0 bis A |
| 0 % — autoliquidation | Sous-traitant BTP pour un donneur d'ordre assujetti : la facture est HT, c'est le client qui paie la TVA | CGI art. 283, 2 nonies |
| 0 % — franchise | Entreprise sous les seuils de franchise : « TVA non applicable, art. 293 B du CGI » | CGI art. 293 B |

Pour 10 % et 5,5 %, le client doit attester que le logement a plus de 2 ans. L'appli rappelle de conserver l'attestation, sans la gérer.
Attention : le CGI est recodifié au 1er janvier 2027, donc les numéros d'articles changeront, mais pas les taux.

## 3. Structure de coût d'un chantier (méthode du déboursé sec)

```
Matériaux et fournitures                    ┐
Main d'œuvre (heures × taux horaire chargé) │
Sous-traitance                              ├─ Déboursé sec (DS) = coûts directs du chantier
Matériel et location                        │
Frais de chantier (carburant, repas, benne) ┘
+ Frais généraux = DS × % (réglage entreprise : loyer, assurances, véhicules, compta…)
= Coût de revient
Chiffre d'affaires HT validé − Coût de revient = Bénéfice (en € et en %)
```

- Chaque dépense a un **poste** : ce sont les catégories actuelles, rebaptisées. Carburant et repas deviennent des frais de chantier.
- La **main d'œuvre** devient une vraie saisie : date, personne, heures. Le coût vaut heures × taux horaire chargé (réglage entreprise, modifiable sur chaque saisie).
- L'**aide au prix** dans le devis : prix conseillé = coût prévu × (1 + frais généraux) × (1 + marge visée).

## 4. Devis : le chiffrage

- Un devis = **des lignes** : désignation, quantité, unité, prix unitaire HT, taux de TVA, et en option le **coût prévu** et son poste (pour le prévu / réel).
- Totaux : total HT, TVA **par taux**, TTC, remise en % en option.
- Statuts : **Brouillon** (tout est modifiable) → **Envoyé** → **Signé** (verrouillé) ou **Refusé**.
- **Modifier les prix d'un devis signé** passe par une **nouvelle version** : copie modifiable, l'ancienne passe en « Remplacé ». Pour des travaux ajoutés en cours de chantier, on fait un **avenant** (travaux en plus).
- Les anciens devis à montant unique deviennent un devis d'une ligne. Le montant stocké est du TTC, repassé en HT au taux par défaut du chantier.
- Devis de sous-traitant : mêmes lignes, et un réglage **« autoliquidation »** qui force la TVA à 0.

## 5. Dépenses : le réel

- Saisie terrain inchangée : photo, montant TTC lu. En plus, le **taux de TVA** (proposé selon la facture ou le réglage, modifiable de 0 à x) et le **poste**.
- L'appli calcule HT = TTC ÷ (1 + taux). Si la facture affiche la TVA, c'est ce montant lu qui sert.
- **Validation** : une dépense saisie depuis le terrain ou par lecture automatique est **« à vérifier »**. Tu la valides en un geste depuis Gérer → Dépenses. Le réel compte toutes les dépenses, et un compteur signale celles à vérifier.
- Refacturation et majoration inchangées, mais calculées sur le **HT**.

## 6. Facturation (suivi interne)

- Types : **acompte** (% du total validé ou montant), **situation** (% d'avancement cumulé), **solde**.
- Chaque ligne : base HT, TVA, TTC, **retenue de garantie** en option (0 à 5 % du TTC, réglée par chantier, loi du 16/07/1971, rendue 1 an après la réception), numéro de la vraie facture, statuts **À facturer → Facturé → Encaissé** avec dates.
- Indicateurs : facturé, encaissé, **reste à facturer**, **retenues à récupérer**, en retard de paiement.

## 7. Tableau de bord du chantier (inspiré de Spendesk, Projul, ProjeQtOr)

Pour chaque poste : **Budget · Dépensé · Engagé · Disponible**, avec une jauge colorée (verte, orange à 80 %, rouge au-delà de 100 %).
En tête : avancement des travaux comparé au % du budget consommé, et prévision du coût final.

| Poste | Prévu | Réel | Écart |
|---|---|---|---|
| Matériaux, Main d'œuvre, Sous-traitance, Matériel, Frais de chantier | coûts prévus des devis signés | dépenses + heures | en € et en %, rouge au-delà |
| **Déboursé sec** | | | |
| Frais généraux (%) | | | |
| **Coût de revient** | | | |
| **CA HT validé** | devis signés + avenants acceptés + refacturation | | |
| **Bénéfice** | € et % | € et % | |

Puis : facturé / encaissé / reste à facturer / retenues, et la TVA collectée et déductible (pour information).

## 8. Réglages (entreprise et chantier)

- **Entreprise** : régime de TVA (assujetti / franchise), taux horaire chargé, frais généraux en %, marge visée en %, TVA par défaut.
- **Chantier** : TVA par défaut (20 / 10 / 5,5…), retenue de garantie en %, mode du matériel (réel / forfait) et majoration (existant), budget matériel (remplacé par le prévu des devis, gardé si aucun devis n'a de coût prévu).

## 9. Stockage

Rien ne change : journal sans écrasement, un fichier par saisie, synchro Drive.
Les nouveaux champs (`ht_centimes`, `tva_bp` en centièmes de %, `poste`, `lignes`, `valide`…) s'ajoutent aux événements existants, et les anciens événements restent lisibles.

## Chantier « entre proches »

Fonctionnement identique à un chantier professionnel : devis en lignes, dépenses, heures, travaux en plus,
budget par poste, bénéfice, acompte / situation / solde, PDF. Seule différence : aucune TVA (chantier non déclaré).
Tous les taux sont forcés à 0 %, les coûts sont comptés au prix payé, pas de mention TVA sur le PDF.
La main d'œuvre au forfait se met comme une ligne du devis (poste « Main d'œuvre »).
