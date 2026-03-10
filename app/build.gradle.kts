import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10" // Utilisez la même version que votre Kotlin
    id("kotlin-parcelize")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Fonction pour obtenir la date formatée
fun getBuildDate(): String {
    return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
        Date()
    )
}

android {
    namespace = "fr.matthstudio.themeteo"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.matthstudio.themeteo"
        minSdk = 26
        targetSdk = 36
        versionCode = 24
        versionName = "2.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Configuration pour le build de débogage
        debug {
            // Le suffixe sera ajouté au nom de l'application (ex: Themeteo-Alpha)
            applicationIdSuffix = ".debug"
            // Le suffixe sera ajouté au nom de la version.
            versionNameSuffix = "-${getBuildDate()}"
            isDebuggable = true // Cette ligne est implicite pour le debug, mais la laisser est clair
            buildConfigField("Boolean", "FIREBASE_ENABLED", "false")
        }

        // Configuration pour le build de production
        release {
            // Ajout du suffixe pour la version de release
            // Note : Pas de suffixe pour l'applicationID en release
            versionNameSuffix = "-${getBuildDate()}"

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "FIREBASE_ENABLED", "true")
        }

        // configuration pour le build de production sans Firebase
        create("releaseNoFirebase") {
            initWith(getByName("release"))
            buildConfigField("Boolean", "FIREBASE_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    ndkVersion = "27.3.13750724"

    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/AL2.0")
            excludes.add("META-INF/LGPL2.1")
            excludes.add("META-INF/third-party-licenses/**")
            // You might encounter similar issues with other META-INF files.
            // Common ones to exclude if they cause conflicts:
            //excludes.add("META-INF/LICENSE")
            // excludes.add("META-INF/LICENSE.txt")
            // excludes.add("META-INF/NOTICE")
            // excludes.add("META-INF/NOTICE.txt")
            // excludes.add("META-INF/*.kotlin_module") // If using libraries not yet fully compatible with AGP
        }
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // Localisation GPS
    // Google Play Services Location API
    implementation(libs.play.services.location)
    // Pour la gestion des permissions avec Compose
    implementation(libs.accompanist.permissions)

    // Osmdroid pour la carte satellite
    implementation(libs.osmdroid.android)

    // Google Maps API pour la carte de choix de lieu
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Dépendance de base pour Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Ktor pour les requêtes réseau
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio) // Moteur HTTP pour Ktor
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.okhttp)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.photoview)
    // Icônes de base (Menu, ArrowBack, etc.)
    implementation(libs.androidx.compose.material.icons.core)
    // TOUTES les autres icônes
    implementation(libs.androidx.compose.material.icons.extended)

    // Glance pour les widgets d'écran d'accueil
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // WorkManager pour exécuter des tâches en arrière-plans
    implementation(libs.androidx.work.runtime.ktx)

    // Data store
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences) // Alternative plus simple pour clé-valeur
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.material.core)
    implementation(libs.androidx.compose.foundation)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
}

// Disable Google Services and Crashlytics tasks for debug builds
// and releaseNoFirebase builds
afterEvaluate {
    tasks.matching {
        val taskName = it.name.lowercase()
        (taskName.contains("googleservices") || taskName.contains("crashlytics")) && 
        (taskName.contains("debug") || taskName.contains("releasenofirebase"))
    }.configureEach {
        enabled = false
    }
}