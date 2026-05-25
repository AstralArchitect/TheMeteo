# Documentation Complète de l'Application TheMeteo

## 1. Introduction
TheMeteo est une application Android moderne et performante offrant des prévisions météorologiques détaillées, des cartes de précipitations en temps réel, des images satellites et des alertes de vigilance. Développée en Kotlin avec Jetpack Compose, elle privilégie une interface utilisateur fluide et réactive.

## 2. Identité Visuelle, Licensing & Branding
L'application arbore une identité visuelle "Hardware-Fusion" unique.
- **Concept :** Fusion entre la nature (nuage) et la technologie (circuits intégrés).
- **Implémentation :** Utilisation de Vector Drawables complexes (`ic_launcher_foreground.xml`) avec des effets de tracés de circuits et de terminaux circulaires sur la moitié gauche du logo.
- **Icônes Adaptatives :** Support complet des icônes adaptatives et monochromes (`ic_launcher_monochrome.xml`) pour une cohérence parfaite avec Material You sur Android 13+.
- **Licence et Copyright :** Standardisation juridique sur l'ensemble du projet. Chaque fichier source Kotlin arbore l'en-tête `Copyright (C) 2026 AstralArchitect`, garantissant une couverture légale complète.

## 3. Architecture Technique
L'application suit le pattern architectural **MVVM (Model-View-ViewModel)** recommandé par Google, organisé par fonctionnalités (feature-based packaging).

### Technologies Clés
- **UI :** Jetpack Compose pour une interface déclarative et réactive. La gestion de nombreux composants UI complexes a été externalisée (ex. `Dialogs.kt`).
- **Réseau :** Ktor Client pour les appels API.
- **Injection de Dépendances :** Gestion manuelle via `AppDataContainer` (Service Locator pattern).
- **Persistance :** Jetpack DataStore pour les préférences (incluant la persistance des paramètres environnementaux comme l'AQI) et `WeatherCache` pour les données météo.
- **Concurrence :** Kotlin Coroutines et Flow.
- **Widgets :** Jetpack Glance pour les widgets d'écran d'accueil avec colorimétrie dynamique.
- **Localisation :** Google Play Services Location avec gestion dynamique de la précision (`HIGH_ACCURACY` vs `BALANCED`) selon les permissions.

## 4. Gestion des Données & Cache (`WeatherCache`)
Le composant `WeatherCache` est le cœur réactif de l'application. Il gère :
- **Double Mise en Cache :** Indexation par `LocationIdentifier` (Favori ou Position GPS) et par `ModelName`.
- **Logique de Rafraîchissement :**
    - Données considérées obsolètes après **1 heure** en excluant les variations de secondes/nanosecondes pour plus de fiabilité.
    - Vérification granulaire de la présence des blocs horaires et journaliers.
    - Rafraîchissement automatique au changement de modèle ou de lieu.
- **Mode Hors-Ligne & Tolérance aux Pannes :**
    - Priorité systématique au cache local pour un affichage immédiat.
    - Émission de données en cache (plutôt que des erreurs bloquantes) en cas de perte momentanée du signal GPS.
- **Chaîne de Modèles de Secours (Model Chain Fallback) :** Un mécanisme de résilience robuste. Si le modèle principal échoue, le système itère automatiquement sur plusieurs modèles de secours pour récupérer les données, garantissant une fiche météo complète même en cas de panne partielle de l'API.

## 5. Structure du Projet
Le code est structuré de manière modulaire dans `app/src/main/java/fr/matthstudio/themeteo/` :

- **`forecastMainActivity/` :** Écran principal, avec l'extraction récente des logiques complexes dans `ActivityMainElements.kt`, `UiElements.kt`, et `Dialogs.kt` pour les interfaces modales (Air Quality, Sun/Moon).
- **`dayChoserActivity/` :** Liste des prévisions sur 15 jours, support des requêtes "Full Period".
- **`dayGraphsActivity/` :** Analyse détaillée via des graphiques interactifs étendus.
- **`rainMapActivity/` :** Carte radar de pluie (RainViewer API).
- **`satImgs/` :** Affichage des images satellites (EUMETSAT).
- **`data/` :** Couche de données (Repositories, DataStore, Providers). Notamment `LocationProvider.kt` qui gère les fallback sur les dernières positions connues.
- **`ui/` :** Thèmes et éléments de design globaux.
- **`telemetry/` :** Gestion des logs d'erreurs (Firebase Crashlytics).
- **`utilClasses/` :** Modèles de données (POJO/Data Classes) et **moteurs de calcul astronomiques** (`MoonCalculator.kt`, `SunCalculator.kt`).

## 6. Intégrations API
TheMeteo agrège des données provenant de plusieurs sources :
- **Open-Meteo :** Source primaire pour les prévisions déterministes, probabilistes et marines.
- **Google Maps API :** Qualité de l'air (Pollution) avec choix de la norme (AQI Google ou FRA/EUR) et Pollen.
- **Météo-France :** Alertes de vigilance et modèles AROME/ARPEGE.
- **RainViewer :** Tuiles radar pour les précipitations.
- **EUMETSAT :** Flux d'images satellites (WMS).

## 7. Fonctionnalités Avancées
- **Modèles d'Ensemble :** Visualisation des incertitudes via le ruban d'incertitude (Spread).
- **Adaptation Géographique :** Basculement automatique sur un modèle mondial ("Auto") si le modèle régional sélectionné n'est plus disponible pour les coordonnées actuelles.
- **Calculs Astronomiques de Haute Précision :** 
  - Utilisation de `MoonCalculator` basé sur l'époque J2000 pour calculer avec exactitude l'âge lunaire, l'illumination, et les phases.
  - Outil `FullSunCalculator` mesurant l'élévation zénithale, la durée de jour, et rendant possible la trajectoire sinusoïdale de l'ensoleillement même pour les pôles.
  - L'intégration du module `SunMoonCompass` combinant les senseurs de rotation et le magnétomètre du téléphone pour offrir une boussole météo augmentée.
- **Prévisions Étendues (Période Complète) :** Support de la visualisation horaire sur la totalité de l'horizon de prédiction d'un modèle (jusqu'à 15 jours).

## 8. Maintenance et Debugging
L'application utilise `TelemetryManager` pour le suivi des performances et la résolution des bugs via Crashlytics.

### Variantes de Build
- **debug** : Logs HTTP actifs, Firebase désactivé.
- **release** : Version de production complète.
- **releaseNoFirebase** : Version sans services Google (Flag `FIREBASE_ENABLED` à `false`).