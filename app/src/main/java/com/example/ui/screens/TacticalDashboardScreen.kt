package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActionStatus
import com.example.ui.components.ActionQueueCard
import com.example.ui.components.ScreenScannerDialog
import com.example.ui.components.TacticalRadarView
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RedstoneDanger
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XpGold
import com.example.viewmodel.CopilotViewModel

@Composable
fun TacticalDashboardScreen(
    viewModel: CopilotViewModel,
    onNavigateToOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.playerTelemetry.collectAsState()
    val actionQueue by viewModel.actionQueue.collectAsState()
    val isAutoRunning by viewModel.isAutoQueueRunning.collectAsState()
    val queueSpeed by viewModel.queueSpeedMs.collectAsState()
    val threatBlips by viewModel.threatRadarBlips.collectAsState()
    val activeThreat by viewModel.selectedThreat.collectAsState()
    val teamMembers by viewModel.teamMembers.collectAsState()
    val multiplayerThreats by viewModel.multiplayerThreats.collectAsState()
    val radarMode by viewModel.radarMode.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val lastScan by viewModel.lastScanResult.collectAsState()


    var showScannerDialog by remember { mutableStateOf(false) }

    if (showScannerDialog) {
        ScreenScannerDialog(
            isScanning = isScanning,
            onDismiss = { showScannerDialog = false },
            onAnalyze = { bitmap, query, scenario ->
                viewModel.runVisionScan(bitmap, query, scenario)
                showScannerDialog = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Telemetry HUD Bar
        item {
            TelemetryHeader(telemetry = telemetry)
        }

        // Quick Emergency Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Emergency Bunker Button
                Button(
                    onClick = { viewModel.triggerEmergencyBunker() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedstoneDanger),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("emergency_bunker_button")
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BUNKER", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Gemini Screen Scanner Button
                Button(
                    onClick = { showScannerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("scan_screen_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ObsidianVoid, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SCAN SCREEN", color = ObsidianVoid, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Overlay Launcher
                Button(
                    onClick = onNavigateToOverlay,
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DiamondCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(44.dp)
                        .testTag("launch_hud_overlay_button")
                ) {
                    Text("HUD OVERLAY", color = DiamondCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Last Vision Scan Summary if available
        if (lastScan != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, DiamondCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "VISION ASSESSMENT",
                                color = DiamondCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "STATUS: ${lastScan!!.healthEstimate}",
                                color = EmeraldGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastScan!!.summary,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 360° Tactical Threat & Multiplayer Squad Radar
        item {
            TacticalRadarView(
                blips = threatBlips,
                activeThreat = activeThreat,
                teamMembers = teamMembers,
                multiplayerThreats = multiplayerThreats,
                radarMode = radarMode,
                onRadarModeChange = { viewModel.setRadarMode(it) },
                onTeamMemberSelected = { member ->
                    viewModel.toggleSquadBeacon(member.id)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }


        // Action Queue Header & Controls
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HIGH-SPEED AUTOMATED ACTION QUEUE",
                            color = EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        val pendingCount = actionQueue.count { it.status == ActionStatus.PENDING }
                        Text(
                            text = "$pendingCount DIRECTIVES PENDING • SPEED: ${queueSpeed}ms",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Auto Play / Pause Toggle
                    Button(
                        onClick = { viewModel.toggleAutoQueue() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAutoRunning) EmeraldGreen else ObsidianCard
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp).testTag("toggle_auto_queue")
                    ) {
                        Icon(
                            imageVector = if (isAutoRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isAutoRunning) ObsidianVoid else TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAutoRunning) "AUTO" else "PAUSED",
                            color = if (isAutoRunning) ObsidianVoid else TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Queue Speed Selector Row & Step Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SpeedChip("TURBO 350ms", queueSpeed == 350L) { viewModel.setQueueSpeed(350L) }
                        SpeedChip("NORMAL 1s", queueSpeed == 1000L) { viewModel.setQueueSpeed(1000L) }
                        SpeedChip("TACTICAL 3s", queueSpeed == 3000L) { viewModel.setQueueSpeed(3000L) }
                    }

                    Button(
                        onClick = { viewModel.stepNextAction() },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DiamondCyan),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp).testTag("step_next_button")
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, tint = DiamondCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("STEP", color = DiamondCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Action Queue List Items
        itemsIndexed(actionQueue, key = { _, action -> action.id }) { index, action ->
            ActionQueueCard(
                action = action,
                isCurrent = index == 0,
                onExecute = { viewModel.executeActionImmediately(action) },
                onDismiss = { viewModel.dismissAction(action.id) }
            )
        }
    }
}

@Composable
private fun SpeedChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) DiamondCyan else ObsidianVoid)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) ObsidianVoid else TextSecondary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TelemetryHeader(telemetry: com.example.data.model.PlayerTelemetry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("telemetry_hud_bar")
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
                        .background(EmeraldGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = telemetry.dimension.label.uppercase(),
                    color = EmeraldGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• ${telemetry.biome}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                text = "DAY ${telemetry.gameDay}",
                color = XpGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Coordinates & Light Level Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianVoid, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "XYZ: ${telemetry.x.toInt()} / ${telemetry.y.toInt()} / ${telemetry.z.toInt()}",
                color = DiamondCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "HP: ${telemetry.health}/20",
                    color = RedstoneDanger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "LIGHT: ${telemetry.lightLevel}",
                    color = if (telemetry.lightLevel == 0) RedstoneDanger else XpGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
