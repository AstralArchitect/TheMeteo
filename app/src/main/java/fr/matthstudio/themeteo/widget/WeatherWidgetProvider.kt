package fr.matthstudio.themeteo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastViewer.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastViewer.weatherCodeToSimpleWord
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

// --- Receiver : Gère le cycle de vie du widget ---
class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    private val WIDGET_PERIODIC_WORK_TAG = "WeatherWidgetPeriodicWorker"
    private val WIDGET_INITIAL_WORK_TAG = "WeatherWidgetInitialWorker"

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // --- Tâche de mise à jour immédiate ---
        val initialWorkRequest = OneTimeWorkRequestBuilder<WeatherWidgetWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WIDGET_INITIAL_WORK_TAG,
            ExistingWorkPolicy.KEEP,
            initialWorkRequest
        )

        // --- Tâche périodique (inchangée) ---
        val periodicWorkRequest = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(
            repeatInterval = Duration.ofHours(1)
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // On annule bien les deux types de tâches pour être propre
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_PERIODIC_WORK_TAG)
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_INITIAL_WORK_TAG)
    }
}

// --- Action pour le clic sur le bouton d'actualisation ---
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        // On lance la même tâche de mise à jour immédiate que dans onUpdate
        val initialWorkRequest = OneTimeWorkRequestBuilder<WeatherWidgetWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "WeatherWidgetInitialWorker", // Le nom doit être identique
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            initialWorkRequest
        )
    }
}

class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 1. Obtenir une instance de WorkManager
        val workManager = WorkManager.getInstance(context)

        // 2. Demander les informations sur notre worker unique
        // getWorkInfosForUniqueWork est une API asynchrone (ListenableFuture), nous devons la convertir.
        val workInfoFuture: ListenableFuture<List<WorkInfo>> = workManager.getWorkInfosForUniqueWork(WeatherWidgetWorker.UNIQUE_WORK_NAME)

        val workInfo = suspendCoroutine { continuation ->
            workInfoFuture.addListener({
                try {
                    // On ne s'intéresse qu'au premier (et unique) résultat
                    continuation.resume(workInfoFuture.get().firstOrNull())
                } catch (e: Exception) {
                    continuation.resume(null)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        // 3. Déterminer si la mise à jour est en cours
        val isUpdating = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED
        Log.d("WeatherWidget", "Is worker running or enqueued? $isUpdating (State: ${workInfo?.state})")

        // Le reste de la logique ne change pas
        val dataFile = File(context.cacheDir, WeatherWidgetWorker.DATA_FILE)
        val widgetData: WidgetData? = try {
            if (dataFile.exists()) Json.decodeFromString<WidgetData>(dataFile.readText()) else null
        } catch (e: Exception) {
            Log.e("WeatherWidget", "Error reading data file", e)
            null
        }

        provideContent {
            GlanceTheme {
                WeatherWidgetUi(widgetData, isUpdating, context)
            }
        }
    }
    @Composable
    private fun WeatherWidgetUi(data: WidgetData?, isUpdating: Boolean, context: Context) {
        val alpha = 0.5f
        val backgroundColor = GlanceTheme.colors.surface.getColor(context).copy(alpha = alpha)
        // --- CONTENU PRINCIPAL ---
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(8.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            if (data != null) {
                // Icône Météo
                val weatherWord = weatherCodeToSimpleWord(data.wmoCode)
                val iconId = getIconForWeather(weatherWord)
                Image(
                    provider = ImageProvider(iconId),
                    contentDescription = "Icône météo actuelle",
                    modifier = GlanceModifier
                        .width(58.dp)
                        .height(58.dp),
                    contentScale = ContentScale.Fit
                )

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    // Température et Nom de la ville
                    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        Row {
                            Text(
                                text = "${data.temperature.roundToInt()}°",
                                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Text(
                                text = "${data.apparentTemperature?.roundToInt()}°",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = "${String.format("%.2f", data.location.first)}, ${String.format("%.2f", data.location.second)}",
                            style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // --- HEURE DE MISE À JOUR ---
                // Calcule le temps relatif (ex: "il y a 5 minutes")
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    data.lastUpdateTimeMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()


                if (!isUpdating)
                    Text(
                        text = "Mise à jour : $relativeTime",
                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                else
                    Text(
                        text = "Mise à jour...",
                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )

                // Si l'heure de mise à jour est plus ancienne que 5 minutes, on affiche un bouton pour actualiser
                if (System.currentTimeMillis() - data.lastUpdateTimeMillis > 5 * 60 * 1000)
                {
                    // On connecte le bouton à notre nouvelle ActionCallback
                    Button(
                        text = "Actualiser",
                        onClick = actionRunCallback<RefreshAction>()
                    )
                }
            } else {
                // On utilise un ProgressBar Android natif.
                // Le `indeterminate` style est le cercle qui tourne.
                /*AndroidRemoteViews(
                    remoteViews = android.widget.RemoteViews(
                        context.packageName,
                        android.R.layout.simple_list_item_1 // Un layout simple qui contient une TextView
                    ).apply {
                        // On réutilise la vue pour y mettre une ProgressBar à la place du texte
                        setViewVisibility(android.R.id.text1, android.view.View.GONE)
                    },
                    containerViewId = android.R.id.text1, // Un ID de vue valide à l'intérieur du layout
                    content = {
                        // Ce composable n'est pas utilisé mais est requis par la signature
                    }
                )*/

                // On peut ajouter un texte si on veut
                Text(
                    text = "Chargement...",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(top = 8.dp)
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                // On connecte le bouton à notre nouvelle ActionCallback
                Button(
                    text = "Actualiser",
                    onClick = actionRunCallback<RefreshAction>()
                )
            }
        }
    }

    /**
     * Helper pour choisir la bonne icône en fonction de la météo.
     * Assurez-vous d'avoir ces icônes dans votre dossier res/drawable.
     */
    @Composable
    private fun getIconForWeather(weatherWord: SimpleWeatherWord): Int {
        return when (weatherWord) {
            SimpleWeatherWord.SUNNY -> R.drawable.clear_day
            SimpleWeatherWord.SUNNY_CLOUDY -> R.drawable.cloudy_3_day
            SimpleWeatherWord.CLOUDY -> R.drawable.cloudy
            SimpleWeatherWord.DUST -> R.drawable.dust
            SimpleWeatherWord.FOGGY -> R.drawable.fog
            SimpleWeatherWord.DRIZZLY -> R.drawable.rainy_1
            SimpleWeatherWord.RAINY1 -> R.drawable.rainy_2
            SimpleWeatherWord.RAINY2 -> R.drawable.rainy_3
            SimpleWeatherWord.HAIL -> R.drawable.hail
            SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3 -> R.drawable.snowy_2
            SimpleWeatherWord.SNOWY_MIX -> R.drawable.snowy_2
            SimpleWeatherWord.STORMY -> R.drawable.thunderstorms
        }
    }
}