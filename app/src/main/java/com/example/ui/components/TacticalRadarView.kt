package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MultiplayerThreat
import com.example.data.model.RadarDisplayMode
import com.example.data.model.TeamMember
import com.example.data.model.ThreatBlip
import com.example.data.model.ThreatSeverity
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NetheritePurple
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RadarGrid
import com.example.ui.theme.RedstoneDanger
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XpGold
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TacticalRadarView(
    blips: List<ThreatBlip>,
    activeThreat: ThreatBlip?,
    teamMembers: List<TeamMember> = emptyList(),
    multiplayerThreats: List<MultiplayerThreat> = emptyList(),
    radarMode: RadarDisplayMode = RadarDisplayMode.ALL_ENTITIES,
    onRadarModeChange: (RadarDisplayMode) -> Unit = {},
    onBlipSelected: (ThreatBlip) -> Unit = {},
    onTeamMemberSelected: (TeamMember) -> Unit = {},
    onMultiplayerThreatSelected: (MultiplayerThreat) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val showMobs = radarMode == RadarDisplayMode.ALL_ENTITIES || radarMode == RadarDisplayMode.MOBS_ONLY
    val showMultiplayer = radarMode == RadarDisplayMode.ALL_ENTITIES || radarMode == RadarDisplayMode.SQUAD_AND_PVP

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
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
                        .background(
                            when {
                                multiplayerThreats.isNotEmpty() -> RedstoneDanger
                                blips.any { it.severity == ThreatSeverity.FATAL || it.severity == ThreatSeverity.HIGH } -> RedstoneDanger
                                else -> EmeraldGreen
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (radarMode == RadarDisplayMode.SQUAD_AND_PVP) "MULTIPLAYER SERVER RADAR" else "360° TACTICAL THREAT RADAR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (radarMode == RadarDisplayMode.SQUAD_AND_PVP) DiamondCyan else EmeraldGreen,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "${(if (showMobs) blips.size else 0) + (if (showMultiplayer) teamMembers.size + multiplayerThreats.size else 0)} SIGNALS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = DiamondCyan,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Radar Mode Selector Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RadarDisplayMode.entries.forEach { mode ->
                val isSelected = radarMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) ObsidianVoid else ObsidianCard)
                        .border(1.dp, if (isSelected) DiamondCyan else ObsidianBorder, RoundedCornerShape(6.dp))
                        .clickable { onRadarModeChange(mode) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (isSelected) DiamondCyan else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Radar Canvas
        Box(
            modifier = Modifier
                .size(240.dp)
                .testTag("tactical_radar_canvas"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f - 8f

                // Draw background circle
                drawCircle(
                    color = ObsidianVoid,
                    radius = radius,
                    center = center
                )

                // Concentric range rings (16m, 32m, 48m, 64m)
                val rings = listOf(0.25f, 0.5f, 0.75f, 1.0f)
                val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                rings.forEach { fraction ->
                    drawCircle(
                        color = RadarGrid,
                        radius = radius * fraction,
                        center = center,
                        style = Stroke(width = 1.2f, pathEffect = if (fraction < 1.0f) dashed else null)
                    )
                }

                // Crosshairs
                drawLine(
                    color = RadarGrid,
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y + radius),
                    strokeWidth = 1f
                )
                drawLine(
                    color = RadarGrid,
                    start = Offset(center.x - radius, center.y),
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = 1f
                )

                // Sweeping beam
                val rad = Math.toRadians(sweepAngle.toDouble())
                val sweepEnd = Offset(
                    (center.x + radius * cos(rad)).toFloat(),
                    (center.y + radius * sin(rad)).toFloat()
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (radarMode == RadarDisplayMode.SQUAD_AND_PVP) DiamondCyan.copy(alpha = 0.8f) else EmeraldGreen.copy(alpha = 0.8f),
                            Color.Transparent
                        ),
                        start = center,
                        end = sweepEnd
                    ),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2.5f
                )

                // Draw Player icon at center
                drawCircle(
                    color = DiamondCyan,
                    radius = 4f,
                    center = center
                )
                drawCircle(
                    color = DiamondCyan.copy(alpha = 0.3f),
                    radius = 8f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // Draw Friendly Team Members (Cyan/Emerald Pulsars with Beacon Rings)
                if (showMultiplayer) {
                    teamMembers.forEach { member ->
                        val normDist = (member.distanceBlocks / 64f).coerceIn(0.12f, 0.92f) * radius
                        val memberRad = Math.toRadians((member.angleDegrees - 90.0))
                        val pos = Offset(
                            (center.x + normDist * cos(memberRad)).toFloat(),
                            (center.y + normDist * sin(memberRad)).toFloat()
                        )

                        // Outer beacon pulse
                        if (member.isBeaconActive) {
                            drawCircle(
                                color = DiamondCyan.copy(alpha = 0.4f),
                                radius = 12f * pulseScale,
                                center = pos,
                                style = Stroke(width = 1.5f)
                            )
                        }

                        // Team halo
                        drawCircle(
                            color = EmeraldGreen.copy(alpha = 0.35f),
                            radius = 7f * pulseScale,
                            center = pos
                        )
                        // Core team blip
                        drawCircle(
                            color = DiamondCyan,
                            radius = 5.5f,
                            center = pos
                        )
                    }

                    // Draw Enemy Multiplayer Threats (Crimson Hexagon/Rings)
                    multiplayerThreats.forEach { threat ->
                        val normDist = (threat.distanceBlocks / 64f).coerceIn(0.15f, 0.95f) * radius
                        val threatRad = Math.toRadians((threat.angleDegrees - 90.0))
                        val pos = Offset(
                            (center.x + normDist * cos(threatRad)).toFloat(),
                            (center.y + normDist * sin(threatRad)).toFloat()
                        )

                        drawCircle(
                            color = RedstoneDanger.copy(alpha = 0.5f),
                            radius = 9f * pulseScale,
                            center = pos,
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = RedstoneDanger,
                            radius = 6f,
                            center = pos
                        )
                    }
                }

                // Draw Hostile Mob Blips
                if (showMobs) {
                    blips.forEach { blip ->
                        val normDist = (blip.distanceBlocks / 64f).coerceIn(0.1f, 0.95f) * radius
                        val blipRad = Math.toRadians((blip.angleDegrees - 90.0))
                        val blipPos = Offset(
                            (center.x + normDist * cos(blipRad)).toFloat(),
                            (center.y + normDist * sin(blipRad)).toFloat()
                        )

                        val blipColor = blip.severity.color

                        // Outer pulse
                        drawCircle(
                            color = blipColor.copy(alpha = 0.35f),
                            radius = 8f * pulseScale,
                            center = blipPos
                        )
                        // Core blip
                        drawCircle(
                            color = blipColor,
                            radius = 5f,
                            center = blipPos
                        )
                    }
                }
            }

            // Directional labels
            Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp), fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("W", modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp), fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text("E", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp), fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Threat Alert Banner (PvP or Mob)
        val pvpAlert = multiplayerThreats.firstOrNull()
        val mobAlert = blips.minByOrNull { it.distanceBlocks }

        if (showMultiplayer && pvpAlert != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianCard)
                    .border(1.dp, RedstoneDanger, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "PvP Alert",
                        tint = RedstoneDanger,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⚔️ RIVAL PLAYER: ${pvpAlert.playerName} (${pvpAlert.distanceBlocks.toInt()}m)",
                                color = RedstoneDanger,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = pvpAlert.armorTier,
                                color = XpGold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "${pvpAlert.velocityDesc} • Armed with ${pvpAlert.weapon}",
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = pvpAlert.tactic,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        } else if (showMobs && mobAlert != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianCard)
                    .border(1.dp, mobAlert.severity.color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Threat Alert",
                        tint = mobAlert.severity.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${mobAlert.name} (${mobAlert.distanceBlocks.toInt()}m)",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = mobAlert.severity.label,
                                color = mobAlert.severity.color,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = mobAlert.tactic,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Squad Status Strip (in Squad mode or All mode)
        if (showMultiplayer && teamMembers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianVoid)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = DiamondCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACTIVE SQUAD TELEMETRY (${teamMembers.size})",
                            color = DiamondCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "TAP FOR BEACON",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                teamMembers.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTeamMemberSelected(member) }
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (member.isBeaconActive) DiamondCyan else EmeraldGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = member.name,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = " (${member.role})",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "HP ${member.health}/${member.maxHealth}",
                                color = if (member.health < 10) RedstoneDanger else EmeraldGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${member.distanceBlocks.toInt()}m",
                                color = DiamondCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
