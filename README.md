# TheMeteo

TheMeteo est une application météo pour Android, développée en Kotlin. Elle fournit des prévisions météorologiques détaillées et précises en utilisant les données de l'API Open-Meteo.

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
- Couverture nuageuse
### Gestion de Lieux Multiples :
- Utilisez votre position GPS actuelle pour des prévisions locales.
- Recherchez et sauvegardez vos villes préférées pour y accéder rapidement.
### Personnalisation du Modèle Météo : 
Choisissez parmi plusieurs modèles de prévision (ECMWF, GFS, Météo-France, etc.) pour comparer les données.

## Aperçu de l'application1.
1. Écran Principal (ForecastMainActivity) :
  - Affiche un résumé de la météo actuelle avec une image de fond dynamique.
  - Permet de sélectionner un lieu (position actuelle ou lieux enregistrés).
  - Présente les prévisions horaires pour la journée.
<br>
<img width="270" height="560" alt="Screenshot_20251223-115417_The meteo" src="https://github.com/user-attachments/assets/8e681382-4fdc-4365-8393-e6caffad95f4" />
<br>
2. Sélecteur de Jour (DayChooserActivity) :
  
  - Liste les prévisions journalières (températures min/max et condition générale) pour les jours à venir.
<br>
<img width="270" height="560" alt="Screenshot_20251223-115459_The meteo" src="https://github.com/user-attachments/assets/5a05dec3-044e-431c-aa74-25fe1bb4c4a7" />
<br>
3. Graphiques Journaliers (DayGraphsActivity) :
  
  - Accessible en cliquant sur un jour spécifique, cette vue offre une analyse visuelle complète avec des graphiques pour toutes les variables météorologiques disponibles sur 24 heures.
<br>
<img width="270" height="563" alt="Screenshot_20251223-115015_The meteo" src="https://github.com/user-attachments/assets/76fd51e0-d4a6-4346-9df7-1d1d36cb6631" />
<br>
4. Selecteur de lieux
  
  - Accessible en cliquant sur le lieu actuel dans la page principale (ATTENTION !!! pour ajouter les lieux (voir le bouton) il faut faire glisser la sheet vers le haut de l'écran)
<br>
<img width="270" height="563" alt="Screenshot_20251223-115015_The meteo" src="https://github.com/user-attachments/assets/e47aa692-3c1f-481f-95aa-6f5826944750" />
<br>
5. Ecran de paramètres
  
  - Accessible via le bouton en forme d'engrenage en haut à droite de l'écran principale
<img width="270" height="210" alt="Screenshot_20251223-121946_The meteo" src="https://github.com/user-attachments/assets/314d3330-c6df-4546-bdc6-81d504163f0c" />

## Structure du projet
Le code source principal est situé dans app/src/main/java/fr/matthstudio/themeteo/forecastViewer.
- ForecastMainActivity.kt: Point d'entrée de l'interface utilisateur principale.
- DayChooserActivity.kt: Affiche la liste des prévisions journalières.
- DayGraphsActivity.kt: Affiche les graphiques détaillés pour une journée.
- WeatherViewModel.kt: Le ViewModel central qui gère l'état et la logique de l'application.
- WeatherService.kt: Gère toutes les communications avec les API externes (Open-Meteo Geocoding et Forecast).
- data/: Contient les classes Repository pour la gestion des données utilisateur (lieux et paramètres).

L'application contient une partie "Images Satellites", un doc est disponible [ici](https://github.com/AstralArchitect/TheMeteo/tree/master/app/src/main/java/fr/matthstudio/themeteo/satImgs)

## Installation
### En compilant soit-même
1. Clonez ce dépôt :
```git clone https://github.com/astralarchitect/TheMeteo.git```
2. Ouvrez le projet avec Android Studio.
3. Laissez Gradle synchroniser et télécharger les dépendances nécessaires.
4. Compilez l'app via Build -> Generate Signed App Bundles or APKs -> APK
5. Vous serez invité à créer une clef, créez en une et sauvegardez la

Note : L'application nécessite les permissions ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION et INTERNET, qui sont déjà déclarées dans AndroidManifest.xml. L'utilisateur sera invité à accorder les permissions de localisation au premier lancement.
### Depuis les releases
Téléchargez l'APK depuis les [releases](https://github.com/AstralArchitect/TheMeteo/releases) puis installez le
