package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.forge.ForgeModBridge
import com.example.data.model.ForgeActionInstruction
import com.example.data.model.ForgeItemStack
import com.example.data.model.NlpParseResult
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
import com.example.viewmodel.CopilotViewModel

@Composable
fun ForgeControlScreen(
    viewModel: CopilotViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputCommand by remember { mutableStateOf("") }
    val isProcessing by viewModel.isNlpProcessing.collectAsState()
    val latestResult by viewModel.latestNlpResult.collectAsState()
    val forgeState by viewModel.forgeBridge.forgeState.collectAsState()
    val isSimulated by viewModel.forgeBridge.isSimulatedMode.collectAsState()
    val host by viewModel.forgeBridge.hostAddress.collectAsState()
    val port by viewModel.forgeBridge.port.collectAsState()
    val isConnected by viewModel.forgeBridge.isConnected.collectAsState()

    var showModCodeDialog by remember { mutableStateOf(false) }

    if (showModCodeDialog) {
        ForgeModCodeDialog(onDismiss = { showModCodeDialog = false })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GEMINI NLP & FORGE MOD API",
                            color = EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { showModCodeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DiamondCyan),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = DiamondCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("FORGE MOD CODE", color = DiamondCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Translates natural language into in-game bot actions: moving, mining, placing blocks, attacking entities, and checking inventory.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Natural Language Input Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .testTag("nlp_command_input_card")
            ) {
                Text(
                    text = "NATURAL LANGUAGE PLAYER COMMAND:",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputCommand,
                        onValueChange = { inputCommand = it },
                        placeholder = {
                            Text(
                                "e.g. 'Mine some stone', 'Build a shelter', 'Kill the creeper'",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("nlp_text_field")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (inputCommand.isNotBlank()) {
                                viewModel.processNlpCommand(inputCommand)
                                inputCommand = ""
                            }
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("nlp_submit_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = ObsidianVoid, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = ObsidianVoid, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Phrasing Variation Chips
                Text(
                    text = "TRY PHRASING VARIATIONS:",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NlpSampleChip("Mine some stone") { viewModel.processNlpCommand("Mine some stone") }
                    NlpSampleChip("Build a shelter") { viewModel.processNlpCommand("Build a shelter") }
                    NlpSampleChip("Kill the creeper") { viewModel.processNlpCommand("Kill the creeper") }
                    NlpSampleChip("Place torches") { viewModel.processNlpCommand("Place torches") }
                }
            }
        }

        // Gemini Feedback & Execution Plan Card
        if (latestResult != null) {
            item {
                NlpResultDisplayCard(
                    result = latestResult!!,
                    onExecuteAll = { viewModel.executeAllForgeActions(latestResult!!.forgeActions) },
                    onExecuteSingle = { action -> viewModel.executeSingleForgeAction(action) }
                )
            }
        }

        // Live Forge Player Telemetry & Inventory
        item {
            ForgeTelemetryCard(state = forgeState)
        }

        // Surroundings 3D Voxel Scanner (Blocks & Entities)
        item {
            ForgeSurroundingsCard(state = forgeState)
        }

        // Forge Connection Settings
        item {
            ForgeConnectionSettingsCard(
                isSimulated = isSimulated,
                host = host,
                port = port,
                isConnected = isConnected,
                onUpdateConfig = { newHost, newPort, sim ->
                    viewModel.forgeBridge.updateConfig(newHost, newPort, sim)
                },
                onTestPing = {
                    Toast.makeText(context, "Pinging Forge server...", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun NlpResultDisplayCard(
    result: NlpParseResult,
    onExecuteAll: () -> Unit,
    onExecuteSingle: (ForgeActionInstruction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.2.dp, EmeraldGreen, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("nlp_result_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EmeraldGreen.copy(alpha = 0.2f))
                        .border(1.dp, EmeraldGreen, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "INTENT: ${result.intent}",
                        color = EmeraldGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "${(result.confidence * 100).toInt()}% CONFIDENCE",
                    color = DiamondCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onExecuteAll,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(28.dp).testTag("execute_all_forge_actions_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ObsidianVoid, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("RUN ALL (${result.forgeActions.size})", color = ObsidianVoid, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Natural Language Player Feedback Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianVoid)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = XpGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GEMINI BOT FEEDBACK TO PLAYER:",
                        color = XpGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${result.feedbackMessage}\"",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        if (result.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            result.warnings.forEach { warn ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedstoneDanger, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = warn, color = RedstoneDanger, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Executable Action Instructions List
        Text(
            text = "EXECUTABLE FORGE ACTIONS (${result.forgeActions.size} STEPS):",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        result.forgeActions.forEachIndexed { idx, act ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianCard)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(act.actionType.color.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = act.actionType.label,
                            color = act.actionType.color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${idx + 1}. ${act.description}",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = act.inGameCommand,
                            color = DiamondCyan,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = { onExecuteSingle(act) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ForgeTelemetryCard(state: com.example.data.model.ForgePlayerState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("forge_telemetry_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = DiamondCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FORGE PLAYER STATE & INVENTORY",
                    color = DiamondCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "HP: ${state.health.toInt()}/${state.maxHealth.toInt()} • HUNGER: ${state.hunger}",
                color = RedstoneDanger,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Inventory Grid (Slots 0 to 8: Hotbar)
        Text(
            text = "ACTIVE HOTBAR SLOTS (0-8):",
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.inventory.take(6).forEach { item ->
                val isSelected = item.slot == state.selectedSlot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) ObsidianVoid else ObsidianCard)
                        .border(1.dp, if (isSelected) DiamondCyan else ObsidianBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "[${item.slot + 1}]",
                            color = if (isSelected) DiamondCyan else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.displayName,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.maxDurability > 100) {
                            Text(
                                text = "DUR: ${item.durability}/${item.maxDurability}",
                                color = XpGold,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "x${item.count}",
                            color = EmeraldGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ForgeSurroundingsCard(state: com.example.data.model.ForgePlayerState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("forge_surroundings_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Radar, contentDescription = null, tint = XpGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "3D SURROUNDINGS & ENTITY SCANNER",
                    color = XpGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "${state.surroundings.nearbyEntities.size} MOBS • ${state.surroundings.nearbyBlocks.size} BLOCKS",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mobs Nearby
        Text(text = "DETECTED ENTITIES WITHIN RANGE:", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(3.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.surroundings.nearbyEntities.forEach { mob ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (mob.isHostile) RedstoneDanger.copy(alpha = 0.15f) else ObsidianCard)
                        .border(1.dp, if (mob.isHostile) RedstoneDanger else ObsidianBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${mob.name} (${mob.distance.toInt()}m) HP:${mob.health.toInt()}",
                        color = if (mob.isHostile) RedstoneDanger else TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Voxel Blocks Nearby
        Text(text = "SURROUNDING VOXEL BLOCKS:", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(3.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.surroundings.nearbyBlocks.take(4).forEach { block ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "• ${block.blockName.replace("minecraft:", "")}",
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "rel (${block.relX}, ${block.relY}, ${block.relZ})",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ForgeConnectionSettingsCard(
    isSimulated: Boolean,
    host: String,
    port: Int,
    isConnected: Boolean,
    onUpdateConfig: (String, Int, Boolean) -> Unit,
    onTestPing: () -> Unit
) {
    var hostInput by remember(host) { mutableStateOf(host) }
    var portInput by remember(port) { mutableStateOf(port.toString()) }
    var simMode by remember(isSimulated) { mutableStateOf(isSimulated) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FORGE BRIDGE CONNECTION",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "SIMULATOR", color = if (simMode) EmeraldGreen else TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = simMode,
                    onCheckedChange = {
                        simMode = it
                        onUpdateConfig(hostInput, portInput.toIntOrNull() ?: 25585, it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = ObsidianCard)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (simMode)
                "Running in Built-in Realistic World Voxel Simulator. All actions, mining, inventory changes, and entity combat are executed locally with instant feedback."
            else
                "Connecting to live Minecraft Forge companion mod at http://$hostInput:$portInput.",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun NlpSampleChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = DiamondCyan,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ForgeModCodeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val code = ForgeModBridge.getForgeModJavaTemplate()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "MINECRAFT FORGE MOD CODE",
                color = EmeraldGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column {
                Text(
                    text = "Drop this companion bridge mod into your Minecraft Forge 1.20+ client to enable full real-time telemetry and bi-directional bot command execution:",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianVoid)
                        .padding(8.dp)
                ) {
                    Text(
                        text = code,
                        color = DiamondCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Forge Mod Code", code))
                    Toast.makeText(context, "Copied Forge Mod source code!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ObsidianVoid, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("COPY CODE", color = ObsidianVoid, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = TextMuted)
            }
        },
        containerColor = ObsidianSurface
    )
}
