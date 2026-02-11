package fr.matthstudio.themeteo.utilsActivities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On récupère la valeur de manière synchrone
        val userSettings = (this.application as TheMeteo).container.userSettingsRepository
        val currentScreen = runBlocking { userSettings.defaultScreen.first() } ?: fr.matthstudio.themeteo.DefaultScreen.FORECAST_MAIN

        val intent = if (currentScreen == fr.matthstudio.themeteo.DefaultScreen.DAY_CHOSER) {
            Intent(this, DayChooserActivity::class.java).apply {
                putExtra("LAUNCHER", true)
            }
        } else {
            Intent(this, ForecastMainActivity::class.java).apply {
                putExtra("LAUNCHER", true)
            }
        }

        startActivity(intent)
        finish()
    }
}