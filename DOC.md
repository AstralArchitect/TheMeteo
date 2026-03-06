# Documentation Complète de l'Application TheMeteo

## 1. Introduction
TheMeteo est une application Android moderne et performante offrant des prévisions météorologiques détaillées, des cartes de précipitations en temps réel, des images satellites et des alertes de vigilance. Développée en Kotlin avec Jetpack Compose, elle privilégie une interface utilisateur fluide et réactive.

## 2. Identité Visuelle & Branding
L'application arbore une identité visuelle "Hardware-Fusion" unique.
- **Concept :** Fusion entre la nature (nuage) et la technologie (circuits intégrés).
- **Implémentation :** Utilisation de Vector Drawables complexes (`ic_launcher_foreground.xml`) avec des effets de tracés de circuits et de terminaux circulaires sur la moitié gauche du logo.
- **Icônes Adaptatives :** Support complet des icônes adaptatives et monochromes (`ic_launcher_monochrome.xml`) pour une cohérence parfaite avec Material You sur Android 13+.

## 3. Architecture Technique
L'application suit le pattern architectural **MVVM (Model-View-ViewModel)** recommandé par Google, organisé par fonctionnalités (feature-based packaging).

### Technologies Clés
- **UI :** Jetpack Compose pour une interface déclarative et réactive.
- **Réseau :** Ktor Client pour les appels API.
- **Injection de Dépendances :** Gestion manuelle via `AppDataContainer` (Service Locator pattern).
- **Persistance :** Jetpack DataStore pour les préférences et `WeatherCache` pour les données météo.
- **Concurrence :** Kotlin Coroutines et Flow.
- **Widgets :** Jetpack Glance pour les widgets d'écran d'accueil.
- **Localisation :** Google Play Services Location.

## 4. Gestion des Données & Cache (`WeatherCache`)
Le composant `WeatherCache` est le cœur réactif de l'application. Il gère :
- **Double Mise en Cache :** Indexation par `LocationIdentifier` (Favori ou Position GPS) et par `ModelName`.
- **Logique de Rafraîchissement :**
    - Données considérées obsolètes après **1 heure**.
    - Vérification granulaire de la présence des blocs horaires et journaliers.
    - Rafraîchissement automatique au changement de modèle ou de lieu.
- **Mode Hors-Ligne :** Priorité systématique au cache local pour un affichage immédiat, avec mise à jour en arrière-plan.
- **Fusion de Modèles (Fallback) :** Capacité à fusionner les données de deux modèles différents pour compléter les variables manquantes, garantissant une fiche technique toujours pleine.

## 5. Structure du Projet
Le code est structuré de manière modulaire dans `app/src/main/java/fr/matthstudio/themeteo/` :

- **`forecastMainActivity/` :** Écran principal (Résumé & Horaires).
- **`dayChoserActivity/` :** Liste des prévisions sur 15 jours.
- **`dayGraphsActivity/` :** Analyse détaillée via des graphiques interactifs.
- **`rainMapActivity/` :** Carte radar de pluie (RainViewer API).
- **`satImgs/` :** Affichage des images satellites (EUMETSAT).
- **`data/` :** Couche de données (Repositories, DataStore, Providers).
- **`ui/` :** Thèmes et éléments de design globaux.
- **`telemetry/` :** Gestion des logs d'erreurs (Firebase Crashlytics).
- **`utilClasses/` :** Modèles de données (POJO/Data Classes).

## 6. Intégrations API
TheMeteo agrège des données provenant de plusieurs sources :
- **Open-Meteo :** Source primaire pour les prévisions déterministes et d'ensemble.
- **Google Maps API :** Qualité de l'air (Pollution) et Pollen.
- **Météo-France :** Alertes de vigilance et modèles AROME/ARPEGE.
- **RainViewer :** Tuiles radar pour les précipitations.
- **EUMETSAT :** Flux d'images satellites (WMS).

## 7. Fonctionnalités Avancées
- **Modèles d'Ensemble :** Visualisation des incertitudes via le ruban d'incertitude (Spread).
- **Adaptation Géographique :** Basculement automatique sur un modèle mondial si le modèle régional sélectionné n'est plus disponible pour les coordonnées GPS actuelles.

## 8. Maintenance et Debugging
L'application utilise `TelemetryManager` pour le suivi des performances et la résolution des bugs via Crashlytics.

### Variantes de Build
- **debug** : Logs HTTP actifs, Firebase désactivé.
- **release** : Version de production complète.
- **releaseNoFirebase** : Version sans services Google (Flag `FIREBASE_ENABLED` à `false`).
