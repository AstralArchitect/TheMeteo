/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.utilsActivities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class CreditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TheMeteoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CreditScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    // État pour stocker le texte de la licence récupéré
    var licenseText by remember { mutableStateOf("Chargement de la licence...") }

    // Chargement dynamique depuis GitHub
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val rawUrl = "https://raw.githubusercontent.com/AstralArchitect/TheMeteo/refs/heads/master/LICENSE"
                licenseText = URL(rawUrl).readText()
            } catch (e: Exception) {
                licenseText = "Impossible de charger la licence. Vérifiez votre connexion internet."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crédits & Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize()
        ) {
            // --- REMERCIEMENTS ---
            Text(
                text = stringResource(R.string.remerciement_1),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = stringResource(R.string.remerciement_2),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // --- LICENCE PERSO ---
            SectionHeader("Conditions d'Utilisation l'Application")

            Text(
                text = "© 2026 AstralArchitect. Tous droits réservés.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Code Source : Distribué sous licence GNU GPL v3.0. Vous pouvez consulter, modifier et redistribuer le code conformément aux termes de cette licence.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "• Design & Logo : Le logo 'TheMeteo' et l'identité visuelle sont la propriété exclusive d'AstralArchitect et ne peuvent être reproduits sans autorisation.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- SECTION DONNÉES ---
            SectionHeader("Données Météo & Environnement")

            ClickableLink(
                prefix = "• Prévisions : Weather data by ",
                linkText = "Open-Meteo.com",
                url = "https://open-meteo.com/"
            )

            // Attributions obligatoires pour Google Maps Platform (Pollen & Air Quality)
            ClickableLink(
                prefix = "• Pollen & Qualité de l'air : ",
                linkText = "Google Maps Platform",
                url = "https://mapsplatform.google.com/"
            )

            ClickableLink(
                prefix = "• Radar : Radar data provided by ",
                linkText = "RainViewer",
                url = "https://www.rainviewer.com/"
            )

            Text(
                text = "• Satellites : © EUMETSAT 2026",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- SECTION CARTOGRAPHIE ---
            SectionHeader("Cartographie")

            ClickableLink(
                prefix = "• Cartes : ",
                linkText = "Google Maps (Maps SDK for Android)",
                url = "https://developers.google.com/maps/documentation/android-sdk"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- SECTION GRAPHISMES ---
            SectionHeader("Design & Graphismes")

            Text(
                text = "Développé par AstralArchitect",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            ClickableLink(
                prefix = "• Pack d'icônes (Metecons by Basmilius) : ",
                linkText = "Meteocons",
                url = "https://github.com/basmilius/weather-icons"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Note : Les icônes ont pu être modifiés par AstralArchitect pour s'adapter au design de l'application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- SECTION LÉGALE (LICENCES) ---
            Text(
                text = "Mentions Légales (MIT License)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = MIT_LICENSE_CONTENT,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION LICENCE PERSO ---
            Text("Licence de TheMeteo", style = MaterialTheme.typography.labelLarge)

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (licenseText == "Chargement de la licence...") {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally))
                }
                Text(
                    text = licenseText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun ClickableLink(prefix: String, linkText: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        append(prefix)
        pushStringAnnotation(tag = "URL", annotation = url)
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )
        ) {
            append(linkText)
        }
        pop()
    }

    ClickableText(
        text = annotatedString,
        style = TextStyle(
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier.padding(vertical = 4.dp),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        }
    )
}

const val MIT_LICENSE_CONTENT = """
Sauf mention contraire, les ressources graphiques de cette application sont distribuées sous licence MIT.

MIT License

Copyright (c) 2020-2024 Bas Milius

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
"""