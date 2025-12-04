# TheMeteo

TheMeteo est une application météo pour Android, développée en Kotlin avec Jetpack Compose. Elle fournit des prévisions météorologiques détaillées et précises en utilisant les données de l'API Open-Meteo.

## Table des matières
- Fonctionnalités
- Aperçu de l'application
- Architecture et technologies
- Structure du projet
- Installation
- Dépendances

## Fonctionnalités
### Météo Actuelle et à 24h :
Visualisez les conditions météorologiques actuelles et les prévisions heure par heure pour les prochaines 24 heures.
### Prévisions Multi-Jours : 
Obtenez un aperçu des prévisions pour les 15 prochains jours.
### Graphiques Détaillés : 
Explorez des graphiques détaillés pour de nombreuses variables :
- Température (réelle et ressentie)
- Point de rosée et humidité
- Précipitations (pluie, neige, probabilité)
- Couverture nuageuse (basse, moyenne, haute)
- Pression atmosphérique
- Vitesse et direction du vent
### Gestion de Lieux Multiples :
- Utilisez votre position GPS actuelle pour des prévisions locales.
- Recherchez et sauvegardez vos villes préférées pour y accéder rapidement.
### Personnalisation du Modèle Météo : 
Choisissez parmi plusieurs modèles de prévision (ECMWF, GFS, Météo-France, etc.) pour comparer les données.
### Interface Moderne : 
Une interface utilisateur propre et intuitive construite avec Jetpack Compose et Material 3.

## Aperçu de l'application1.
1. Écran Principal (ForecastMainActivity) :
  - Affiche un résumé de la météo actuelle avec une image de fond dynamique.
  - Permet de sélectionner un lieu (position actuelle ou lieux enregistrés).
  - Présente les prévisions horaires pour la journée.
2. Sélecteur de Jour (DayChooserActivity) :
  - Liste les prévisions journalières (températures min/max et condition générale) pour les jours à venir.
3. Graphiques Journaliers (DayGraphsActivity) :
  - Accessible en cliquant sur un jour spécifique, cette vue offre une analyse visuelle complète avec des graphiques pour toutes les variables météorologiques disponibles sur 24 heures.

## Architecture et technologies
L'application suit une architecture moderne centrée sur l'UI avec Jetpack Compose et le pattern MVVM (Model-View-ViewModel).
- Langage : Kotlin•UI : Jetpack Compose pour une interface déclarative et moderne.
- Architecture : MVVM◦ViewModel (WeatherViewModel) : Gère la logique métier, l'état de l'interface et communique avec le service de données.
  - Repository (UserLocationsRepository, UserSettingsRepository) : Abstrait les sources de données (DataStore, réseau).
  - Service (WeatherService) : Gère la communication réseau avec l'API Open-Meteo.•Réseau : Ktor pour des requêtes HTTP asynchrones et robustes.
- Sérialisation : Kotlinx.serialization pour parser les réponses JSON de l'API.
- Asynchrone : Coroutines Kotlin pour la gestion des tâches de fond (réseau, base de données).
- Gestion de l'état : StateFlow et collectAsState pour une communication réactive entre le ViewModel et l'UI.
- Persistance des données : Jetpack DataStore pour sauvegarder les lieux et les préférences utilisateur.
- Localisation : Google Play Services Location pour récupérer la position de l'utilisateur.
- Chargement d'images : Coil pour charger et afficher efficacement les images et icônes (y compris les SVG).

## Structure du projet
Le code source principal est situé dans app/src/main/java/fr/matthstudio/themeteo/forecastViewer.
- ForecastMainActivity.kt: Point d'entrée de l'interface utilisateur principale.
- DayChooserActivity.kt: Affiche la liste des prévisions journalières.
- DayGraphsActivity.kt: Affiche les graphiques détaillés pour une journée.
- WeatherViewModel.kt: Le ViewModel central qui gère l'état et la logique de l'application.
- WeatherService.kt: Gère toutes les communications avec les API externes (Open-Meteo Geocoding et Forecast).
- data/: Contient les classes Repository pour la gestion des données utilisateur (lieux et paramètres).

## Installation
1. Clonez ce dépôt :
```git clone https://github.com/astralarchitect/TheMeteo.git```
2. Ouvrez le projet avec Android Studio.
3. Laissez Gradle synchroniser et télécharger les dépendances nécessaires.
4. Exécutez l'application sur un émulateur ou un appareil Android (API 26+).
Note : L'application nécessite les permissions ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION et INTERNET, qui sont déjà déclarées dans AndroidManifest.xml. L'utilisateur sera invité à accorder les permissions de localisation au premier lancement.

## Dépendances
Voici une liste non-exhaustive des dépendances utilisées dans ce projet :
- Jetpack Compose: activity-compose, compose-bom, material3, ui-graphics.
- Ktor: ktor-client-core, ktor-client-cio, ktor-client-content-negotiation.
- Kotlinx Serialization: kotlinx-serialization-json.•Coil: coil-compose, coil-svg.
- Google Play Services: play-services-location pour la géolocalisation.
- Accompanist: accompanist-permissions pour une gestion simplifiée des permissions avec Compose.
- Jetpack DataStore: datastore-preferences pour le stockage des préférences.
<p>Pour la liste complète, veuillez consulter le fichier app/build.gradle.kts.</p>
