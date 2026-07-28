/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun RainMapLegend(modifier: Modifier = Modifier) {
    val legendItems = listOf(
        LegendItem("Extrême", Color(0xFFFFFFFF)),
        LegendItem("Diluvien", Color(0xFFB400BE)),
        LegendItem("Très forte", Color(0xFFE61E1E)),
        LegendItem("Forte", Color(0xFFFF7800)),
        LegendItem("Soutenue", Color(0xFFFFD200)),
        LegendItem("Modérée", Color(0xFF28B44B)),
        LegendItem("Faible", Color(0xFF3282F0)),
        LegendItem("Bruine", Color(0xFFB4DCFF))
    )

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        legendItems.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(item.color, shape = RoundedCornerShape(2.dp))
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview
@Composable
fun RainMapLegendPreview() {
    MaterialTheme {
        RainMapLegend()
    }
}

private data class LegendItem(val label: String, val color: Color)
