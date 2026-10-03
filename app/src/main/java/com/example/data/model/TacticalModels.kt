package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NetheritePurple
import com.example.ui.theme.RedstoneDanger
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XpGold

enum class ActionPriority(val label: String, val color: Color) {
    CRITICAL("CRITICAL", RedstoneDanger),
    COMBAT("COMBAT", Color(0xFFFF7675)),
    SURVIVAL("SURVIVAL", XpGold),
    RESOURCE("RESOURCE", DiamondCyan),
    NAVIGATION("NAVIGATION", EmeraldGreen)
}

enum class ActionStatus {
    PENDING,
    EXECUTING,
    COMPLETED,
    CANCELLED
}

data class TacticalAction(
    val id: String,
    val title: String,
    val description: String,
    val priority: ActionPriority,
    val status: ActionStatus = ActionStatus.PENDING,
    val command: String,
    val durationMs: Long = 1000L,
    val progress: Float = 0f,
    val hotbarSlot: Int? = null,
    val icon: String = "bolt"
)

enum class Dimension(val label: String) {
    OVERWORLD("Overworld"),
    NETHER("The Nether"),
    THE_END("The End")
}

data class PlayerTelemetry(
    val health: Int = 18,
    val maxHealth: Int = 20,
    val hunger: Int = 16,
    val armor: Int = 15,
    val biome: String = "Plains / Lush Cave Border",
    val dimension: Dimension = Dimension.OVERWORLD,
    val x: Double = 124.5,
    val y: Double = -54.0,
    val z: Double = -312.2,
    val lightLevel: Int = 4,
    val facing: String = "North (Yaw -180.0)",
    val gameDay: Int = 42,
    val timeOfDay: String = "Midnight (22:45)"
)

enum class ThreatSeverity(val label: String, val color: Color) {
    LOW("Safe", EmeraldGreen),
    MEDIUM("Caution", XpGold),
    HIGH("Danger", Color(0xFFE67E22)),
    FATAL("IMMINENT THREAT", RedstoneDanger)
}

data class ThreatBlip(
    val id: String,
    val name: String,
    val type: String, // Creeper, Warden, Skeleton, Enderman, Spider
    val distanceBlocks: Float,
    val angleDegrees: Float, // 0 to 360 relative to player facing
    val severity: ThreatSeverity,
    val tactic: String
)

data class OreDepthData(
    val name: String,
    val optimalY: Int,
    val rangeMinY: Int,
    val rangeMaxY: Int,
    val color: Color,
    val densityCurve: Float, // 0f to 1f representation
    val tip: String
)

data class GameMasterQuest(
    val id: String,
    val title: String,
    val tier: String,
    val lore: String,
    val objectives: List<String>,
    val rewards: List<String>,
    val isCompleted: Boolean = false
)

data class VisionScanResult(
    val summary: String,
    val biomeDetected: String,
    val healthEstimate: String,
    val threatLevel: ThreatSeverity,
    val detectedEntities: List<String>,
    val tacticalDirectives: List<String>,
    val recommendedActions: List<TacticalAction>
)

// --- Minecraft Forge Modding API & NLP Models ---

data class ForgeItemStack(
    val slot: Int,
    val itemId: String,
    val count: Int,
    val durability: Int = 100,
    val maxDurability: Int = 100,
    val displayName: String = itemId.replace("minecraft:", "").replace("_", " ").capitalize()
)

data class ForgeBlockVoxel(
    val relX: Int,
    val relY: Int,
    val relZ: Int,
    val blockName: String,
    val isSolid: Boolean = true,
    val isHazard: Boolean = blockName.contains("lava") || blockName.contains("fire")
)

data class ForgeEntityNearby(
    val entityId: Int,
    val name: String,
    val type: String,
    val relX: Float,
    val relY: Float,
    val relZ: Float,
    val distance: Float,
    val health: Float = 20f,
    val isHostile: Boolean = true
)

data class ForgeSurroundings(
    val lightLevel: Int = 7,
    val biome: String = "Plains",
    val nearbyBlocks: List<ForgeBlockVoxel> = emptyList(),
    val nearbyEntities: List<ForgeEntityNearby> = emptyList()
)

data class ForgePlayerState(
    val health: Float = 20f,
    val maxHealth: Float = 20f,
    val hunger: Int = 18,
    val saturation: Float = 10f,
    val x: Double = 120.0,
    val y: Double = 64.0,
    val z: Double = -250.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val dimension: String = "minecraft:overworld",
    val selectedSlot: Int = 0,
    val inventory: List<ForgeItemStack> = emptyList(),
    val surroundings: ForgeSurroundings = ForgeSurroundings()
)

enum class ForgeActionType(val label: String, val color: Color) {
    MOVE_TO("MOVE", DiamondCyan),
    LOOK_AT("LOOK", EmeraldGreen),
    MINE_BLOCK("MINE", XpGold),
    PLACE_BLOCK("PLACE", EmeraldGreen),
    ATTACK_ENTITY("ATTACK", RedstoneDanger),
    USE_ITEM("USE", XpGold),
    SELECT_SLOT("HOTBAR", DiamondCyan),
    SNEAK("SNEAK", NetheritePurple),
    JUMP("JUMP", DiamondCyan),
    EXECUTE_COMMAND("CMD", TextSecondary)
}

data class ForgeActionInstruction(
    val id: String,
    val actionType: ForgeActionType,
    val targetX: Double? = null,
    val targetY: Double? = null,
    val targetZ: Double? = null,
    val targetEntityId: Int? = null,
    val slot: Int? = null,
    val blockType: String? = null,
    val delayMs: Long = 400L,
    val description: String,
    val inGameCommand: String
)

data class NlpParseResult(
    val originalCommand: String,
    val intent: String,
    val feedbackMessage: String,
    val confidence: Float,
    val warnings: List<String> = emptyList(),
    val forgeActions: List<ForgeActionInstruction> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

// --- Multiplayer Server Radar Models ---

enum class RadarDisplayMode(val label: String) {
    ALL_ENTITIES("All Vectors"),
    SQUAD_AND_PVP("Squad & PvP"),
    MOBS_ONLY("Hostile Mobs")
}

data class TeamMember(
    val id: String,
    val name: String,
    val role: String = "Scout",
    val health: Int = 20,
    val maxHealth: Int = 20,
    val x: Double,
    val y: Double,
    val z: Double,
    val distanceBlocks: Float,
    val angleDegrees: Float,
    val pingStatus: String = "Normal",
    val isBeaconActive: Boolean = false,
    val holdingItem: String = "Iron Pickaxe"
)

data class MultiplayerThreat(
    val id: String,
    val playerName: String,
    val distanceBlocks: Float,
    val angleDegrees: Float,
    val armorTier: String = "Diamond Armor",
    val weapon: String = "Netherite Sword",
    val velocityDesc: String = "Sprinting toward player",
    val severity: ThreatSeverity = ThreatSeverity.HIGH,
    val tactic: String = "Equip shield and keep bow drawn for distance kiting."
)


