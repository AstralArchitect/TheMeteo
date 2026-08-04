# TheMeteo

TheMeteo est une application météo complète pour Android offrant des prévisions d'une précision chirurgicale, un large choix de modèles météorologiques, des radars de pluie interactifs et une multitude de données environnementales (Pollen, Qualité de l'Air, Vigilance). Le tout dans une interface moderne et personnalisable.

<a href="https://play.google.com/store/apps/details?id=fr.matthstudio.themeteo">
  <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/fr_badge_web_generic.png" width="200"/>
</a>

## Table des matières
- [1. Écran Principal (Météo Actuelle & Horaires)](#1-écran-principal-météo-actuelle--horaires)
- [2. Sélecteur de Lieux & Intelligence Géographique](#2-sélecteur-de-lieux--intelligence-géographique)
- [3. Prévisions Journalières (15 Jours)](#3-prévisions-journalières-15-jours)
- [4. Graphiques Détaillés (Période Complète)](#4-graphiques-détaillés-période-complète)
- [5. Radar de Pluie](#5-radar-de-pluie)
- [6. Images Satellites](#6-images-satellites)
- [7. Mode Hors-Ligne & Cache Intelligent](#7-mode-hors-ligne--cache-intelligent)
- [8. Comprendre les Paramètres](#8-comprendre-les-paramètres)
- [9. Guide des Modèles Météorologiques](#9-guide-des-modèles-météorologiques)

---

## 1. Écran Principal (Météo Actuelle & Horaires)
L'écran principal s'adapte dynamiquement aux conditions actuelles pour vous donner les informations essentielles en un clin d'œil.

### Fonctionnalités :
- **Arrière-plan dynamique :** Le fond d'écran s'anime, se colore et se floute selon la météo et l'heure (jour/nuit).
- **Informations principales :** Icône météo (animée), température actuelle géante et courte description générée intelligemment (ex: "Partiellement nuageux avec pluie légère").
- **Alertes de Vigilance (Météo-France) :** Affichage des vigilances pertinentes en cours pour les départements français. Un clic ouvre une boîte de dialogue avec le détail des horaires et le niveau de gravité.
- **Prévisions horaires rapides :** 
  - Graphiques simplifiés interactifs pour la **Température**, la **Température ressentie**, les **Précipitations** (si > 0) et le **Vent**.
  - Bandeau déroulant des icônes météo heure par heure.
  - Clic sur la carte pour basculer vers les graphiques détaillés.
- **Éphéméride & Astronomie (Soleil et Lune) :** 
  - Affiche l'heure du prochain lever ou coucher du soleil, la durée calculée de la journée, et la phase lunaire.
  - **Dialogue détaillé interactif (clic) :** Accédez à une boussole interactive s'adaptant aux capteurs de votre appareil, ainsi qu'à une visualisation de la trajectoire solaire en onde sinusoïdale.
- **Qualité de l'Air et Pollen :**
  - Aperçu de l'Index de Qualité de l'Air (AQI) avec code couleur et affichage du polluant majeur.
  - **Personnalisation :** Choix entre les standards d'AQI Européen (FRA/EUR) ou Universel (Google) dans les paramètres.
  - Aperçu du risque pollinique actuel.
  - **Dialogue détaillé (clic) :** Liste des concentrations de chaque polluant avec les tendances journalières (Min/Max/Avg), recommandations de santé, et descriptions détaillées des plantes allergènes pour les 3 prochains jours.
- **Mini-carte Radar :** Un aperçu de la carte des pluies avec un bouton pour l'ouvrir en plein écran.
- **Accès Rapides (Bas de page) :** Affichage des coordonnées GPS, de la source de données, et d'un bouton direct pour ouvrir la carte Satellite.

---

## 2. Sélecteur de Lieux & Intelligence Géographique
Accessible en cliquant sur le nom de la ville actuelle en haut de l'écran principal ou journalier.

### Fonctionnalités :
- **Gestion des Favoris :** Liste déroulante des villes sauvegardées affichant directement leur température et météo actuelles.
- **Lieu par défaut :** Au premier lancement, l'application initialise Paris par défaut. Vous pouvez marquer une autre ville avec une étoile (⭐) pour un chargement automatique.
- **Adaptation Automatique du Modèle :** Si vous sélectionnez une ville hors de la zone de couverture de votre modèle actuel, TheMeteo bascule automatiquement sur le modèle "IFS" mondial.
- **Recherche & Carte :** Recherche mondiale par autocomplétion ou sélection manuelle précise sur Google Maps.

---

## 3. Prévisions Journalières (15 Jours*)
Une liste claire, lisible et ergonomique pour anticiper la météo à moyen terme.

### Fonctionnalités :
- **Vue d'ensemble :** Affichage de la date complète, de l'icône météo générale (animée), des températures minimales et maximales, des précipitations totales (mm) et des rafales de vent max (km/h).
- **Indicateurs de records :** Les températures les plus extrêmes de la quinzaine sont mises en évidence par un code couleur (Rouge pour le maximum absolu, Bleu pour le minimum absolu).
- **Période Complète :** Une option "Full period forecast" permet de charger l'intégralité des données horaires sur 15 jours.
- **Interactivité complète :** Chaque carte journalière est cliquable et ouvre instantanément les graphiques détaillés pour ce jour précis, avec un centrage automatique à 6h du matin.

* La periode de prévision peut différer selon le modèle choisit (voir plus bas pour la séléction du modèle météo)

---

## 4. Graphiques Détaillés (Période Complète)
Pour les utilisateurs exigeants qui souhaitent analyser l'évolution heure par heure de n'importe quelle variable météorologique, avec un défilement horizontal infini jusqu'à 15 jours de prévisions.

### Graphiques disponibles (affichés dynamiquement uniquement si des données pertinentes existent) :
- **Température** (Jaune) et **Température Ressentie** (Orange)
- **Précipitations Totales** (Bleu clair). Un menu déroulant permet de scinder cette donnée en sous-graphiques :
  - *Probabilité de précipitations (%)*
  - *Pluie pure (mm)*
  - *Chute de neige (cm/h)*
- **Épaisseur de neige au sol** (Blanc)
- **Vitesse du vent** (Vert). Affiche les km/h sur la courbe, et juste en dessous : des flèches dynamiques indiquant la **direction du vent** et des étiquettes pour les **rafales**.
- **Humidité** (Cyan, de 0 à 100%)
- **Visibilité** (Menthe)
- **Couverture Nuageuse** (Gris, de 0 à 100%)
- **Pression atmosphérique** (Violet)
- **Point de Rosée** (Rouge brique)
- **Opacité** (Gris foncé)
- **Indice UV** (Jaune pâle, échelle standard)

---

## 5. Radar de Pluie
Suivez le déplacement précis des perturbations pluvieuses.

### Fonctionnalités :
- **Carte fluide et interactive :** Déplacement libre, zoom multi-touch. Fond de carte CartoDB optimisé (passe en mode sombre automatiquement selon le système).
- **Géolocalisation :** Un marqueur indique votre position sélectionnée directement sur le radar.

---

## 7. Mode Hors-Ligne & Cache Intelligent
TheMeteo est conçue pour fonctionner de manière fluide, même dans les zones à faible couverture réseau.
- **Chargement instantané :** Les dernières données consultées sont stockées localement et affichées instantanément dès l'ouverture d'un écran.
- **Rafraîchissement intelligent :** L'application ne sollicite le réseau que si les données en cache datent de plus d'une heure ou si des plages horaires sont manquantes, et gère de manière robuste les pertes temporaires de signal GPS.
- **Chaîne de Modèles de Secours (Model Chain Fallback) :** Si votre modèle préféré échoue ou ne propose pas une variable spécifique, TheMeteo complète automatiquement votre fiche en requêtant de façon transparente une cascade de modèles de secours mondiaux, offrant ainsi une expérience toujours ininterrompue et exhaustive.*

* Cela est effectué seulement si le paramètre est activé (le paramètre est activé par défaut).

---

## 8. Comprendre les Paramètres
TheMeteo vous permet de configurer l'application selon votre niveau d'expertise. Voici une explication simple de chaque réglage :

### Type de prévision (Déterministe vs Ensemble)
C'est le réglage le plus puissant de l'application.
- **Mode Déterministe (Standard) :** Le supercalculateur météo calcule un seul scénario, le plus probable. C'est ce que vous voyez sur la plupart des sites météo. C'est précis, mais cela ne vous dit pas si la situation est incertaine.
- **Mode Ensemble (Probabiliste) :** Au lieu de faire un seul calcul, le modèle lance **plusieurs dizaines de simulations** (appelées "membres") en modifiant légèrement les conditions de départ. 
  - *Pourquoi l'utiliser ?* Si tous les membres sont d'accord, la prévision est fiable. S'ils divergent, cela indique une grande incertitude.
  - *Dans l'app :* Vous verrez un **ruban d'incertitude** coloré autour de la courbe. Plus le ruban est large, plus le risque de changement est élevé. Deux icônes s'affichent aussi : le "Pire cas" et le "Meilleur cas".

### Choix du Modèle Météo
Le "Modèle", c'est le cerveau qui traite les données.
- Certains cerveaux sont très bons pour voir les orages locaux mais ne voient pas loin (ex: **AROME** pour la France, 2 jours max).
- D'autres sont des champions du long terme mais manquent de précision sur votre ville exacte (ex: **ECMWF** ou **GFS**, jusqu'à 15 jours).
- *Astuce :* Si vous êtes en France, utilisez **AROME** pour demain, et **ECMWF** pour la semaine prochaine.

### Arrondir les valeurs
- **Activé :** Affiche des températures simples (ex: 22° au lieu de 21.7°).
- **Désactivé :** Affiche la valeur exacte calculée par le modèle.

### Compléter les variables manquantes (Fallback)
C'est une fonction de sécurité qui utilise le système de modèles de secours pour remplir les trous (variables manquantes) éventuels de votre modèle favori.

### Norme AQI (Qualité de l'Air)
Vous permet de basculer entre la norme Européenne (FRA/EUR) et la norme Universelle (Google) pour l'affichage de l'indice de qualité de l'air.

### Icônes animées
Permet d'activer les animations fluides. Désactivé automatiquement en mode "Économie de batterie".

### Affichage par défaut (App Focus)
Personnalisez ce que vous voyez en ouvrant l'application : **Actuel** ou **Journalier**.

---

## 9. Guide des Modèles Météorologiques
TheMeteo propose une sélection exhaustive de modèles issus des plus grands instituts météorologiques mondiaux. Les noms des modèles sont affichés de façon conviviale dans l'interface de l'application.

### Modèles Mondiaux (Global)
*Disponibles pour n'importe quel point du globe.*

| Modèle | Institut | Commentaire |
| :--- | :--- | :--- |
| **Auto** (ex-Meilleur Modèle) | Open-Meteo | **Recommandé par défaut.** Combine intelligemment les meilleures sources selon le lieu. |
| **ECMWF IFS (9km)** | Centre Européen (CEPMMT) | Le modèle haute résolution de référence mondiale, réputé pour sa grande fiabilité. |
| **ECMWF AIFS** | Centre Européen (CEPMMT) | Modèle expérimental basé sur l'Intelligence Artificielle. Très rapide et prometteur. |
| **NCEP GFS** | NOAA (USA) | Le modèle américain historique. Mise à jour fréquente, très utilisé pour le long terme. |
| **ICON Global** | Deutscher Wetterdienst (DWD) | Modèle allemand moderne utilisant une grille triangulaire pour une grande précision physique. |
| **ARPEGE World** | Météo-France | La version mondiale du modèle français, excellent pour les tendances globales. |
| **GEM Global** | Environnement Canada | Modèle canadien robuste, particulièrement performant dans les zones tempérées. |
| **JMA GSM** | Japan Meteorological Agency | Référence pour l'Asie et le Pacifique, mais efficace mondialement. |
| **UKMO Global** | UK Met Office | Modèle britannique de haute précision, souvent parmi les meilleurs scores mondiaux. |

### Modèles Régionaux (Haute Résolution)
*Apparaissent dans la liste uniquement si vous êtes dans la zone géographique couverte.*

| Modèle | Institut | Couverture | Commentaire |
| :--- | :--- | :--- | :--- |
| **AROME / HD** | Météo-France | France & pays frontaliers | **La référence absolue pour la France.** Précision chirurgicale (1.3km) pour les orages. |
| **ARPEGE Europe** | Météo-France | Europe | Plus précis qu'ARPEGE World sur le continent européen. |
| **ICON D2 / EU** | DWD (Allemagne) | Allemagne / Europe | Optimisé pour les phénomènes de méso-échelle en Europe Centrale. |
| **Harmonie Arome** | KNMI / DMI / Met Co-op | Pays-Bas / Danemark / Europe | Modèle collaboratif très précis pour l'Europe du Nord et l'Atlantique. |
| **UKMO UK 2km** | UK Met Office | Royaume-Uni & Irlande | Précision extrême pour les îles britanniques. |
| **MeteoSwiss ICON** | MétéoSuisse | Suisse & Alpes | Le meilleur choix pour la complexité du relief alpin. |
| **GEM Regional** | Environnement Canada | Amérique du Nord | Haute résolution pour le Canada et les USA. |

### Modèles d'Ensemble (Probabilistes)
*Utilisés pour visualiser les incertitudes via le ruban d'incertitude.*

- **ECMWF IFS Ensemble** : 51 simulations différentes.
- **NCEP GEFS Ensemble** : Système probabiliste américain (34 jours).
- **ICON Ensemble** : Version probabiliste du modèle allemand.
- **GEM Ensemble** : Système d'ensemble canadien.
- **UKMO Ensemble** : Version probabiliste britannique.

## ⚖️ Licences

- **Code Source :** Distribué sous licence **GPL v3**. (Consultez le fichier LICENSE). L'ensemble des fichiers sources intègre une notice de copyright unifiée : `Copyright (C) 2026 AstralArchitect`.
- **Logo & Design :** Tous droits réservés à **AstralArchitect**.
- **Crédits Tierces :** Icônes sous licence MIT et données via RainViewer/EUMETSAT.
