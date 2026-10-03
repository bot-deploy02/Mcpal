package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NetheritePurple
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RedstoneDanger
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XpGold
import kotlin.math.abs
import kotlin.math.max

data class OreDensity(
    val name: String,
    val color: Color,
    val getDensity: (Int) -> Float, // Returns 0f..1f for given Y level
    val optimalY: Int,
    val description: String
)

val MinecraftOres = listOf(
    OreDensity(
        name = "Diamond",
        color = DiamondCyan,
        getDensity = { y ->
            if (y in -64..16) {
                // Increases linearly as you go deeper towards -64
                ((16 - y).toFloat() / 80f).coerceIn(0f, 1f)
            } else 0f
        },
        optimalY = -58,
        description = "Optimal mining Y=-58 (just above bedrock & lava lake ceiling Y=-54)"
    ),
    OreDensity(
        name = "Ancient Debris",
        color = NetheritePurple,
        getDensity = { y ->
            if (y in 8..24) {
                (1f - (abs(y - 15) / 10f)).coerceIn(0f, 1f)
            } else 0f
        },
        optimalY = 14,
        description = "Nether optimal Y=14-15 with bed/TNT blast mining"
    ),
    OreDensity(
        name = "Iron",
        color = Color(0xFFD6A2E8),
        getDensity = { y ->
            when (y) {
                in -64..80 -> (1f - (abs(y - 16) / 64f)).coerceIn(0f, 0.85f)
                in 80..320 -> ((y - 80).toFloat() / 240f).coerceIn(0f, 1f)
                else -> 0f
            }
        },
        optimalY = 16,
        description = "Peaks at Y=16 subterranean and Y=232 on mountain peaks"
    ),
    OreDensity(
        name = "Redstone",
        color = RedstoneDanger,
        getDensity = { y ->
            if (y in -64..16) ((16 - y).toFloat() / 80f).coerceIn(0f, 1f) else 0f
        },
        optimalY = -58,
        description = "Increases with depth towards bedrock (-64)"
    ),
    OreDensity(
        name = "Gold",
        color = XpGold,
        getDensity = { y ->
            if (y in -64..32) (1f - (abs(y - (-16)) / 48f)).coerceIn(0f, 0.9f) else 0f
        },
        optimalY = -16,
        description = "Standard caves peak at Y=-16; Badlands surface up to Y=256"
    ),
    OreDensity(
        name = "Coal",
        color = Color(0xFF718093),
        getDensity = { y ->
            if (y in 0..256) (1f - (abs(y - 96) / 100f)).coerceIn(0f, 1f) else 0f
        },
        optimalY = 96,
        description = "Most abundant at Y=96 inside hills and mountains"
    )
)

@Composable
fun OreHeatmapView(
    currentY: Int = -54,
    onYSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var scannedY by remember(currentY) { mutableFloatStateOf(currentY.toFloat()) }
    val intY = scannedY.toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DiamondCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ORE DISTRIBUTION & Y-DEPTH SCANNER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiamondCyan,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "Y = $intY",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (intY == -58) EmeraldGreen else TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Depth Slider (-64 to 320)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("-64", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Slider(
                value = scannedY,
                onValueChange = {
                    scannedY = it
                    onYSelected(it.toInt())
                },
                valueRange = -64f..320f,
                colors = SliderDefaults.colors(
                    thumbColor = DiamondCyan,
                    activeTrackColor = DiamondCyan,
                    inactiveTrackColor = ObsidianBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("ore_depth_slider")
            )
            Text("+320", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        // Quick Preset Depth Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetYButton("Y=-58 (Diamond)", scannedY == -58f) { scannedY = -58f; onYSelected(-58) }
            PresetYButton("Y=14 (Netherite)", scannedY == 14f) { scannedY = 14f; onYSelected(14) }
            PresetYButton("Y=16 (Iron)", scannedY == 16f) { scannedY = 16f; onYSelected(16) }
            PresetYButton("Y=64 (Sea)", scannedY == 64f) { scannedY = 64f; onYSelected(64) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Heatmap Density Bars for Selected Y
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianVoid, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MinecraftOres.forEach { ore ->
                val density = ore.getDensity(intY)
                val percent = (density * 100).toInt()

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ore.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = ore.name,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = if (percent > 0) "$percent% Yield Density" else "0% (Out of Range)",
                            color = if (percent > 0) ore.color else TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ObsidianBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = density.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ore.color)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tactical Tip Card for current Y
        val tip = when {
            intY in -64..-55 -> "Tactical Advice: Peak Diamond & Redstone depth. Mine at Y=-58 to stay 4 blocks below lava lakes (Y=-54) while maximizing diamond node generation."
            intY in -54..-50 -> "WARNING: Active Lava Lake generation zone (Y=-54 to -50). Carry Water Bucket in hotbar slot 4 to prevent sudden burning."
            intY in 10..18 -> "Tactical Advice: Prime Iron mining altitude in Overworld caverns. In Nether, Y=14-15 is optimal for Ancient Debris."
            intY > 200 -> "Tactical Advice: Extreme Mountain peaks. Highest concentration of Emeralds and mountain Iron veins. Beware fall damage and powdered snow."
            else -> "Standard caving tier. Ensure torches are spaced within 6 blocks to prevent hostile mob spawning (Light Level > 0 required)."
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianCard)
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (intY in -54..-50) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (intY in -54..-50) RedstoneDanger else DiamondCyan,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tip,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun PresetYButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) EmeraldGreen else ObsidianCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) ObsidianVoid else TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}
