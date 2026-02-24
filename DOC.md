# Documentation Complète de l'Application TheMeteo

## 1. Introduction
TheMeteo est une application Android moderne et performante offrant des prévisions météorologiques détaillées, des cartes de précipitations en temps réel, des images satellites et des alertes de vigilance. Développée en Kotlin avec Jetpack Compose, elle privilégie une interface utilisateur fluide et réactive.

## 2. Architecture Technique
L'application suit le pattern architectural **MVVM (Model-View-ViewModel)** recommandé par Google, organisé par fonctionnalités (feature-based packaging).

### Technologies Clés
- **UI :** Jetpack Compose pour une interface déclarative et réactive.
- **Réseau :** Ktor Client pour les appels API (remplaçant Retrofit pour une meilleure intégration Kotlin).
- **Injection de Dépendances :** Gestion manuelle via `AppDataContainer` (Service Locator pattern).
- **Persistance :** Jetpack DataStore pour les préférences utilisateur et les lieux enregistrés.
- **Concurrence :** Kotlin Coroutines et Flow pour la gestion asynchrone des flux de données.
- **Widgets :** Jetpack Glance pour les widgets d'écran d'accueil.
- **Localisation :** Google Play Services Location.

## 3. Structure du Projet
Le code est structuré de manière modulaire dans `app/src/main/java/fr/matthstudio/themeteo/` :

- **`forecastMainActivity/` :** Écran principal affichant le résumé météo et les prévisions horaires.
- **`dayChoserActivity/` :** Liste des prévisions sur 15 jours.
- **`dayGraphsActivity/` :** Analyse détaillée via des graphiques interactifs pour chaque jour.
- **`rainMapActivity/` :** Carte radar de pluie en temps réel.
- **`satImgs/` :** Gestion et affichage des images satellites.
- **`data/` :** Couche de données (Repositories, DataStore, Providers).
- **`ui/` :** Thèmes et éléments de design globaux.
- **`telemetry/` :** Gestion des logs d'erreurs et statistiques d'usage (Firebase Crashlytics).
- **`utilClasses/` :** Classes utilitaires et modèles de données transversaux.

## 4. Intégrations API
TheMeteo agrège des données provenant de plusieurs sources de confiance :
- **Open-Meteo :** Prévisions déterministes et d'ensemble, géocodage.
- **Google Maps API :** Qualité de l'air et prévisions polliniques.
- **Météo-France :** Carte de vigilance et alertes départementales.
- **Data.gouv.fr :** Géocodage inverse pour l'identification des départements.
- **RainViewer :** Données radar pour la carte des pluies.

## 5. Fonctionnalités Principales

### Prévisions Avancées
- **Modèles de prévision :** Choix entre ECMWF, GFS, Météo-France (Arome/Arpege), ICON, etc.
- **Prévisions d'ensemble :** Visualisation des incertitudes météorologiques via les modèles d'ensemble.
- **Variables détaillées :** Température (réelle/ressentie), précipitations (type/intensité/probabilité), vent (vitesse/rafales/direction), pression, humidité, couverture nuageuse, visibilité, index UV.

### Analyse Visuelle
- **Graphiques dynamiques :** Représentation visuelle des variables sur 24h pour chaque jour sélectionné.
- **Fonds d'écran dynamiques :** Changement de l'arrière-plan selon les conditions météo actuelles.

### Cartographie & Satellites
- **Radar de pluie :** Visualisation de l'évolution des précipitations sur une carte interactive.
- **Images Satellites :** Accès aux dernières images satellites pour suivre les masses nuageuses.

### Santé & Sécurité
- **Vigilance Météo :** Alertes Météo-France intégrées avec détails par type de risque (vent, orages, etc.).
- **Qualité de l'Air :** Index de pollution atmosphérique local.
- **Pollen :** Niveaux de concentration pollinique par type de plante.

### Expérience Utilisateur
- **Gestion de lieux :** Recherche de villes et sauvegarde en favoris.
- **Mode Offline :** Cache intelligent (`WeatherCache`) pour accéder aux dernières données sans connexion.
- **Widget :** Widget d'accueil personnalisable pour un accès rapide aux infos météo.

## 6. Maintenance et Debugging
L'application intègre un gestionnaire de télémétrie (`TelemetryManager`) qui logue les exceptions et événements critiques dans Firebase Crashlytics pour assurer une stabilité optimale en production.

### Variantes de Build
Le projet utilise plusieurs variantes de build pour s'adapter aux différents besoins :
- **debug** : Version de développement. Inclut un suffixe `.debug` à l'ID d'application et active les logs HTTP détaillés. Firebase est **désactivé**.
- **release** : Version de production standard. Firebase est **activé**.
- **releaseNoFirebase** : Version de production sans dépendance aux services Google/Firebase. Firebase est **désactivé** via le flag `FIREBASE_ENABLED` mis à `false`.

Le flag `FIREBASE_ENABLED` est accessible via `BuildConfig.FIREBASE_ENABLED` dans le code Kotlin.

En mode Debug, les logs HTTP sont activés pour faciliter le développement.
