package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Nature
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity
import fr.matthstudio.themeteo.dayGraphsActivity.DayGraphsActivity
import fr.matthstudio.themeteo.dayGraphsActivity.GraphType
import fr.matthstudio.themeteo.satImgs.MapActivity
import fr.matthstudio.themeteo.utilClasses.AirQualityUI
import fr.matthstudio.themeteo.utilClasses.PollenUI
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilsActivities.WindUnitSetting
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

@Composable
fun BlurredBackground(state: SimpleWeatherWord?, isNight: Boolean = false) {
    val (baseColor, meshColors) = when (state) {
        SimpleWeatherWord.STORMY -> if (isNight) {
            Color(0xFF0D001A) to listOf(
                Color(0xFF311B92).copy(alpha = 0.7f),
                Color(0xFF1A237E).copy(alpha = 0.5f),
                Color(0xFF4A148C).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF1B0033) to listOf(
                Color(0xFF673AB7).copy(alpha = 0.8f),
                Color(0xFF3F51B5).copy(alpha = 0.6f),
                Color(0xFF9C27B0).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.HAIL, SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3, SimpleWeatherWord.SNOWY_MIX -> if (isNight) {
            Color(0xFF101416) to listOf(
                Color(0xFF37474F).copy(alpha = 0.7f),
                Color(0xFF263238).copy(alpha = 0.6f),
                Color(0xFF455A64).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF37474F) to listOf(
                Color(0xFFCFD8DC).copy(alpha = 0.8f),
                Color(0xFF90A4AE).copy(alpha = 0.6f),
                Color(0xFF607D8B).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2, SimpleWeatherWord.DRIZZLY -> if (isNight) {
            Color(0xFF090C29) to listOf(
                Color(0xFF1A237E).copy(alpha = 0.7f),
                Color(0xFF0D47A1).copy(alpha = 0.6f),
                Color(0xFF01579B).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF1A237E) to listOf(
                Color(0xFF3949AB).copy(alpha = 0.8f),
                Color(0xFF5C6BC0).copy(alpha = 0.6f),
                Color(0xFF283593).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.DUST -> if (isNight) {
            Color(0xFF2E1B15) to listOf(
                Color(0xFF5D4037).copy(alpha = 0.7f),
                Color(0xFF4E342E).copy(alpha = 0.6f),
                Color(0xFF3E2723).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF8D6E63) to listOf(
                Color(0xFFBCAAA4).copy(alpha = 0.8f),
                Color(0xFFD7CCC8).copy(alpha = 0.6f),
                Color(0xFFA1887F).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.HAZE -> if (isNight) {
            Color(0xFF1A2124) to listOf(
                Color(0xFF37474F).copy(alpha = 0.7f),
                Color(0xFF263238).copy(alpha = 0.6f),
                Color(0xFF455A64).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF546E7A) to listOf(
                Color(0xFFB0BEC5).copy(alpha = 0.8f),
                Color(0xFFCFD8DC).copy(alpha = 0.6f),
                Color(0xFF90A4AE).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.FOGGY, SimpleWeatherWord.CLOUDY -> if (isNight) {
            Color(0xFF161B1E) to listOf(
                Color(0xFF263238).copy(alpha = 0.7f),
                Color(0xFF37474F).copy(alpha = 0.6f),
                Color(0xFF212121).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF455A64) to listOf(
                Color(0xFF90A4AE).copy(alpha = 0.8f),
                Color(0xFFB0BEC5).copy(alpha = 0.6f),
                Color(0xFF78909C).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.SUNNY_CLOUDY, SimpleWeatherWord.SUNNY -> if (isNight) {
            Color(0xFF000814) to listOf(
                Color(0xFF001D3D).copy(alpha = 0.8f),
                Color(0xFF003566).copy(alpha = 0.6f),
                Color(0xFF1B263B).copy(alpha = 0.5f)
            )
        } else {
            Color(0xFF1565C0) to listOf(
                Color(0xFFFFB74D).copy(alpha = 0.8f),
                Color(0xFFFFD740).copy(alpha = 0.6f),
                Color(0xFF42A5F5).copy(alpha = 0.5f)
            )
        }
        null -> MaterialTheme.colorScheme.background to listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || state == null) {
        // Dégradé simple pour les versions anciennes ou lorsque state est null
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = meshColors
                    )
                )
        )
    } else {
        // Effet de cercles floutés (Mesh) pour les versions récentes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp)
            ) {
                drawCircle(
                    color = meshColors[0],
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.1f, size.height * 0.2f)
                )
                drawCircle(
                    color = meshColors[1],
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.9f, size.height * 0.4f)
                )
                drawCircle(
                    color = meshColors[2],
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.4f, size.height * 0.8f)
                )
            }
        }
    }
}

@Composable
fun HourlyForecastCard(hourlyForecast: WeatherDataState, context: Context, viewModel: WeatherViewModel) {

    if (hourlyForecast is WeatherDataState.Loading) {
        CircularProgressIndicator()
        return
    }
    if (hourlyForecast is WeatherDataState.Error) {
        Text(
            text = hourlyForecast.message
        )
        return
    }
    var variable: ChosenVar by remember { mutableStateOf(ChosenVar.TEMPERATURE) }
    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(true, onClick = { // Make the card clickable
                // Create an Intent to launch DayGraphsActivity
                val intent = Intent(context, DayGraphsActivity::class.java).apply {
                    putExtra("SELECTED_LOCATION", viewModel.selectedLocation.value)
                    putExtra("START_DATE_TIME", LocalDateTime.now())
                }
                // Start the activity
                context.startActivity(intent)
            })
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, top = 0.dp)
        ) {
            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row (
                    modifier = Modifier.padding(start = 12.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.hourly_forecast),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 20.dp, top = 20.dp)
                )
            }

            if ((hourlyForecast as? WeatherDataState.SuccessHourly)?.data?.isEmpty() ?: true)
                return@BentoCard

            val scrollState = rememberScrollState()
            val tempUnit = viewModel.userSettings.collectAsState().value.temperatureUnit
            val windUnit = viewModel.userSettings.collectAsState().value.windUnit
            when (variable) {
                ChosenVar.TEMPERATURE -> GenericGraph(
                    viewModel,
                    tempUnit,
                    windUnit,
                    GraphType.TEMP,
                    Color(0xFFFFF176),
                    scrollState = scrollState
                )

                ChosenVar.APPARENT_TEMPERATURE -> if (hourlyForecast.data.first().apparentTemperature != null)
                    GenericGraph(
                        viewModel,
                        tempUnit,
                        windUnit,
                        GraphType.A_TEMP,
                        Color(0xFFFFB300),
                        scrollState = scrollState
                    )

                ChosenVar.PRECIPITATION -> GenericGraph(
                    viewModel,
                    tempUnit,
                    windUnit,
                    GraphType.PRECIPITATION,
                    Color(0xFF039BE5),
                    valueRange = 0f..3f,
                    scrollState = scrollState
                )

                ChosenVar.WIND -> GenericGraph(
                    viewModel,
                    tempUnit,
                    windUnit,
                    GraphType.WIND_SPEED,
                    Color(0xFF7CB342),
                    scrollState = scrollState
                )
            }
            if (variable != ChosenVar.WIND)
                WeatherIconGraph(viewModel, null, scrollState = scrollState)

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ChosenVar.entries.forEach { buttonVariable ->
                    if (buttonVariable == ChosenVar.APPARENT_TEMPERATURE && hourlyForecast.data.first().apparentTemperature == null)
                        return@forEach
                    val isSelected = variable == buttonVariable

                    if (buttonVariable == ChosenVar.PRECIPITATION && hourlyForecast.data.mapNotNull { it.precipitationData.precipitation }.maxOrNull() == 0.0)
                        return@forEach

                    OutlinedButton(
                        modifier = Modifier
                            .padding(4.dp),
                        enabled = !isSelected,
                        onClick = { variable = buttonVariable }
                    ) {
                        Text(
                            when (buttonVariable) {
                                ChosenVar.TEMPERATURE -> stringResource(R.string.temperature_unit) + " " + UnitConverter.getSymbolWithDegree(tempUnit)
                                ChosenVar.APPARENT_TEMPERATURE -> stringResource(R.string.a_temperature_unit) + " " + UnitConverter.getSymbolWithDegree(tempUnit)
                                ChosenVar.PRECIPITATION -> stringResource(R.string.precipitation_unit)
                                ChosenVar.WIND -> stringResource(R.string.wind_speed_unit) + if (windUnit == WindUnit.MPH) " mph" else if (windUnit == WindUnit.KPH) " kph" else ""
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

// --- Sun & Details ---
// Main Component
@Composable
fun SunAndDetails(viewModel: WeatherViewModel, context: Context, onShowSunMoonDetails: () -> Unit, onShowDetails: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val dailyForecast by viewModel.dailyForecast.collectAsState()
        if (dailyForecast !is WeatherDataState.SuccessDaily)
            return

        val data = (dailyForecast as WeatherDataState.SuccessDaily).data
        if (data.isEmpty())
            return

        // variable to determine if the sun card can be shown
        var isSunOk = true
        // --- Logic for calculating next two sun events ---
        val now = LocalDateTime.now()

        val allEvents = mutableListOf<NextSunEvent>()

        // Today's events (Data index 0)
        val todayReading = data[0]
        todayReading.sunrise.toEventLocalDateTime(context.applicationContext)?.let { dt ->
            allEvents.add(
                NextSunEvent(
                    stringResource(R.string.sunrise),
                    dt,
                    stringResource(R.string.today)
                )
            )
        }
        todayReading.sunset.toEventLocalDateTime(context.applicationContext)?.let { dt ->
            allEvents.add(
                NextSunEvent(
                    stringResource(R.string.sunset),
                    dt,
                    stringResource(R.string.today)
                )
            )
        }

        // Tomorrow's events (Data index 1, if available)
        val tomorrowReading = data.getOrNull(1)
        tomorrowReading?.sunrise?.toEventLocalDateTime(context.applicationContext)?.let { dt ->
            allEvents.add(
                NextSunEvent(
                    stringResource(R.string.sunrise),
                    dt,
                    stringResource(R.string.tomorrow)
                )
            )
        }
        tomorrowReading?.sunset?.toEventLocalDateTime(context.applicationContext)?.let { dt ->
            allEvents.add(
                NextSunEvent(
                    stringResource(R.string.sunset),
                    dt,
                    stringResource(R.string.tomorrow)
                )
            )
        }

        // Filter out past events and sort by time (chronological order)
        val futureEvents = allEvents
            .filter { it.dateTime.isAfter(now) }
            .sortedBy { it.dateTime }
            .take(2)

        var text: String = ""
        if (futureEvents.isEmpty())
            isSunOk = false
        if (futureEvents.size < 2)
            isSunOk = false

        if (isSunOk) {
            when (futureEvents[0].type) {
                stringResource(R.string.sunrise) if tomorrowReading != null -> {
                    val duration = Duration.between(
                        todayReading.sunset.toEventLocalDateTime(context.applicationContext),
                        tomorrowReading.sunrise.toEventLocalDateTime(context.applicationContext)
                    )
                    text =
                        "${stringResource(R.string.night)} : ${duration.toHours()}h ${duration.toMinutes() % 60}min"
                }

                stringResource(R.string.sunset) -> {
                    val duration = Duration.between(
                        todayReading.sunrise.toEventLocalDateTime(context.applicationContext),
                        todayReading.sunset.toEventLocalDateTime(context.applicationContext)
                    )
                    text =
                        "${stringResource(R.string.day)} : ${duration.toHours()}h ${duration.toMinutes() % 60}min"
                }

                else -> {
                    text = ""
                }
            }
        }

        // Determine which events to display
        val displayEvent1 =
            futureEvents.getOrNull(0) ?: allEvents.getOrNull(0)
        val displayEvent2 = futureEvents.getOrNull(1)

        if (text != "" && displayEvent1 != null) {
            SunriseSunsetCard(
                modifier = Modifier.weight(1f),
                event1 = displayEvent1,
                event2 = displayEvent2,
                text,
                onClick = onShowSunMoonDetails
            )
        }

        // La card regroupée (Détails)
        SummaryDetailsCard(
            modifier = Modifier.weight(1f),
            onClick = { onShowDetails() }
        )
    }
}

// Sun Card
@Composable
fun SunriseSunsetCard(modifier: Modifier, event1: NextSunEvent, event2: NextSunEvent?, text: String, onClick: () -> Unit) {
    // Helper function to format the time string required (HH:mm)
    fun LocalDateTime.formatTime(): String {
        return String.format(Locale.getDefault(), "%02d:%02d", this.hour, this.minute)
    }

    BentoCard(modifier = modifier.height(140.dp), onClick = onClick) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sun), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            ResponsiveText(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            // Next event (Event 1)
            Column {
                val time1 = event1.dateTime.formatTime()

                val label1 = if (event1.dayLabel == stringResource(R.string.today)) {
                    stringResource(R.string.sun_event_format, event1.type, time1)
                } else {
                    stringResource(R.string.sun_event_day_format, event1.type, event1.dayLabel, time1)
                }

                ResponsiveText(
                    label1,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                // Second next event (Event 2)
                event2?.let { event ->
                    val time2 = event.dateTime.formatTime()
                    val label2 = if (event.dayLabel == stringResource(R.string.today)) {
                        stringResource(R.string.sun_event_format, event.type, time2)
                    } else {
                        stringResource(R.string.sun_event_day_format, event.type, event.dayLabel, time2)
                    }

                    ResponsiveText(
                        label2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// Details Card
@Composable
fun SummaryDetailsCard(modifier: Modifier, onClick: () -> Unit) {
    BentoCard(
        modifier = modifier
            .height(140.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.details), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Icons from previous cards to hint content
                Icon(Icons.Rounded.Air, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Icon(Icons.Rounded.Compress, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Text(stringResource(R.string.show_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

// --- Daily Forecast Card ---
@Composable
fun DailyForecastCard(viewModel: WeatherViewModel, context: Context) {
    val dailyForecast by viewModel.dailyForecast.collectAsState()
    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        val nDays = 7
        if (dailyForecast == WeatherDataState.Loading) {
            Box(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@BentoCard
        }

        if (dailyForecast is WeatherDataState.Error || (dailyForecast as? WeatherDataState.SuccessDaily)?.data.isNullOrEmpty())
            return@BentoCard

        val dailyData = (dailyForecast as WeatherDataState.SuccessDaily).data
        val minOverallTemp = dailyData.take(nDays).minOf { it.minTemperature ?: 0.0 }
        val maxOverallTemp = dailyData.take(nDays).maxOf { it.maxTemperature ?: 0.0 }
        val userSettings by viewModel.userSettings.collectAsState()
        val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()

        var expandedDayIndex by remember { mutableStateOf(-1) }

        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clickable {
                        val intent = Intent(context, DayChooserActivity::class.java)
                        context.startActivity(intent)
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.daily_forecast),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            dailyData.take(nDays).forEachIndexed { index, dayReading ->
                DailyForecastRow(
                    dayReading = dayReading,
                    isExpanded = expandedDayIndex == index,
                    minOverallTemp = minOverallTemp,
                    maxOverallTemp = maxOverallTemp,
                    userSettings = userSettings,
                    isBatterySaverActive = isBatterySaverActive,
                    onClick = {
                        expandedDayIndex = if (expandedDayIndex == index) -1 else index
                    }
                ) {
                    // Expanded Content: Graph and Details
                    val hourlyForecast by viewModel.getForecastForRange(
                        dayReading.date.atTime(0, 0),
                        dayReading.date.plusDays(1).atTime(0, 0)
                    ).collectAsState(initial = WeatherDataState.Loading)

                    val scrollState = rememberScrollState()
                    val density = LocalDensity.current
                    val hasScrolled = remember { mutableStateOf(false) }

                    LaunchedEffect(hourlyForecast) {
                        // Define the scroll to match with the 6th hour
                        if (hourlyForecast is WeatherDataState.SuccessHourly && !hasScrolled.value) {
                            val data = (hourlyForecast as WeatherDataState.SuccessHourly).data
                            if (data.isNotEmpty() && data.first().time.hour == 0) {
                                val index6h = data.indexOfFirst { it.time.hour == 6 }
                                if (index6h != -1) {
                                    val contentWidthDp = 500.dp
                                    val contentWidthPx = with(density) { contentWidthDp.toPx() }
                                    val xPadding = 40f
                                    val xStep = (contentWidthPx - 2 * xPadding) / (data.size - 1)
                                    val scrollOffset = xPadding + index6h * xStep - (xStep / 2) // Center the scroll between the 5th and 6th hour position
                                    scrollState.scrollTo(scrollOffset.toInt())
                                    hasScrolled.value = true
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.3f
                                ), RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                            .clickable {
                                val intent = Intent(
                                    context,
                                    DayGraphsActivity::class.java
                                ).apply {
                                    putExtra(
                                        "START_DATE_TIME",
                                        dayReading.date.atTime(0, 0)
                                    )
                                    putExtra(
                                        "SELECTED_LOCATION",
                                        viewModel.selectedLocation.value
                                    )
                                }
                                context.startActivity(intent)
                            }
                    ) {
                        Text(
                            text = stringResource(R.string.temperature),
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (hourlyForecast is WeatherDataState.SuccessHourly) {
                            AdvancedGraph(
                                hourlyForecast,
                                userSettings.roundToInt,
                                userSettings.temperatureUnit,
                                userSettings.windUnit,
                                GraphType.TEMP,
                                Color(0xFFFFF176),
                                scrollState = scrollState,
                                contentWidth = 500.dp,
                                contentHeight = 80.dp,
                                compactHourFormat = false
                            )
                            WeatherIconGraph(viewModel,
                                hourlyForecast,
                                scrollState,
                                500.dp,
                                true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Wind
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (dayReading.maxWind.windDirection != null) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Air,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(
                                            dayReading.maxWind.windDirection?.toFloat()
                                                ?.plus(90f) ?: 0f
                                        ),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    UnitConverter.formatWind(
                                        dayReading.maxWind.windspeed,
                                        userSettings.windUnit
                                    ), style = MaterialTheme.typography.labelSmall
                                )
                            }
                            // UV
                            if (dayReading.maxUvIndex != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Rounded.WbSunny,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = getUVColor(dayReading.maxUvIndex)
                                    )
                                    Text(
                                        "UV ${dayReading.maxUvIndex}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            // Sun
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "${dayReading.sunrise.toEventLocalDateTime(context.applicationContext)?.hour}:${
                                        dayReading.sunrise.toEventLocalDateTime(
                                            context.applicationContext
                                        )?.minute
                                    } " +
                                            "/ ${dayReading.sunset.toEventLocalDateTime(context.applicationContext)?.hour}:${
                                                dayReading.sunset.toEventLocalDateTime(
                                                    context.applicationContext
                                                )?.minute
                                            }", style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                if (index < dailyData.size - 1 && index < 9) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

// --- Air Quality And Pollen ---
@Composable
fun AirQualityCard(
    data: AirQualityUI,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Groupe Gauche : Titre et Label
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.air_quality),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = data.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Barre horizontale colorée (Jauge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(data.color.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((data.value.toFloat() / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(data.color)
                    )
                }
            }

            // Groupe Droite : AQI en très grand
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = data.value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    lineHeight = 40.sp
                )
                Text(
                    text = "AQI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PollenCard(
    data: PollenUI,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pollen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOfNotNull(
                    data.tree to Pair(stringResource(R.string.trees), Icons.Rounded.Nature),
                    data.grass to Pair(stringResource(R.string.weed), Icons.Rounded.LocalFlorist),
                    data.weed to Pair(stringResource(R.string.grasses), Icons.Rounded.Grass)
                ).forEach { (type, data) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Box {
                            EnvironmentalGauge(
                                value = (type?.level?.toFloat() ?: 0f) / 4f,
                                color = type?.color ?: MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = data.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.Center).offset(y = 6.dp)
                            )
                            Icon(
                                imageVector = data.second,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).offset(y = (-11).dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${type?.level ?: 0}/4",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Display a short description (ex: Low, Moderate, etc.)
                        Text(
                            text = getPollenShortDescFromLevel(type?.level ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- Additional Infos ---
@Composable
fun AdditionalInfos(viewModel: WeatherViewModel, context: Context) {
    // Informations en bas de page
    Spacer(modifier = Modifier.height(24.dp))
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val locationText = when (val loc = selectedLocation) {
            is LocationIdentifier.CurrentUserLocation -> stringResource(
                R.string.location_coords_format,
                viewModel.userLocation.collectAsState().value?.latitude ?: 0.0,
                viewModel.userLocation.collectAsState().value?.longitude ?: 0.0
            )
            is LocationIdentifier.Saved -> stringResource(
                R.string.location_coords_format,
                loc.location.latitude,
                loc.location.longitude
            )
        }

        ResponsiveText(
            text = locationText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )

        ResponsiveText(
            text = stringResource(R.string.source_format, WeatherModelRegistry.models.firstOrNull { it.apiName == viewModel.userSettings.collectAsState().value.model }?.sourceName ?: viewModel.userSettings.collectAsState().value.model),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(context, MapActivity::class.java)
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Rounded.Map, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_satellite_map))
        }
    }
}

@Composable
fun RainAlertCard(hourlyForecast: WeatherDataState) {
    if (hourlyForecast !is WeatherDataState.SuccessHourly) return

    val now = LocalDateTime.now()
    val next12Hours = hourlyForecast.data.filter { 
        it.time.isAfter(now) && it.time.isBefore(now.plusHours(12)) 
    }

    val firstRain = next12Hours.firstOrNull { (it.precipitationData.precipitation ?: 0.0) > 0.1 }

    if (firstRain != null) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val rainTime = firstRain.time.format(formatter)

        BentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.rain_expected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.rain_at_format, rainTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun BentoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        content()
    }
}