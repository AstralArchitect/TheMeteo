/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity

@Composable
fun RainMapLegend(extended: Boolean, onChangeVisibility: () -> Unit, modifier: Modifier = Modifier) {
    val legendItems = listOf(
        LegendItem("Extrême", Color(0xFFE61E1E), 40, 0),
        LegendItem("Très forte", Color(0xFFFF7800), 25, 40),
        LegendItem("Forte", Color(0xFFFFBF00), 15, 25),
        LegendItem("Abondante", Color(0xFFFFEB00), 8, 15),
        LegendItem("Modérée", Color(0xFF28B44B), 4, 8),
        LegendItem("Faible", Color(0xFF3282F0), 1, 4),
        LegendItem("Bruine", Color(0xFFB4DCFF), 0, 1)
    )

    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (extended) 0.6f else 0.4f)
                )
        )

        if (extended) {
            Column(
                modifier = Modifier
                    .clickable { onChangeVisibility() }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                legendItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(item.color, shape = RoundedCornerShape(2.dp))
                        )
                        Text(
                            modifier = Modifier.width(80.dp),
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        var finalText = ""
                        if (item.mm_min == 0) {
                            finalText = "<${item.mm_max}"
                        } else if (item.mm_max == 0) {
                            finalText = ">${item.mm_min}"
                        } else {
                            finalText += item.mm_min
                            finalText += " to "
                            finalText += item.mm_max
                        }
                        finalText += "mm"
                        Text(
                            text = finalText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .clickable { onChangeVisibility() }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                legendItems.reversed().forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(item.color, shape = RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RainMapLegendPreview() {
    MaterialTheme {
        var extended by remember{ mutableStateOf(true) }
        RainMapLegend(extended, {extended = !extended})
    }
}

private data class LegendItem(val label: String, val color: Color, val mm_min: Int, val mm_max: Int)
