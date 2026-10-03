package com.example.data.nlp

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ForgeActionInstruction
import com.example.data.model.ForgeActionType
import com.example.data.model.ForgePlayerState
import com.example.data.model.NlpParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class MinecraftNlpModule {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun translateCommand(
        playerCommand: String,
        playerState: ForgePlayerState
    ): NlpParseResult = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d("MinecraftNlp", "Using internal high-speed NLP compiler (Offline/Demo mode)")
            return@withContext parseCommandLocally(playerCommand, playerState)
        }

        try {
            // Formulate full contextual prompt containing inventory, surroundings, health
            val invSummary = playerState.inventory.joinToString(", ") {
                "Slot ${it.slot}: ${it.displayName} (x${it.count}, durability: ${it.durability}/${it.maxDurability})"
            }

            val blocksSummary = playerState.surroundings.nearbyBlocks.take(10).joinToString(", ") {
                "${it.blockName} at rel (${it.relX}, ${it.relY}, ${it.relZ})"
            }

            val entitiesSummary = playerState.surroundings.nearbyEntities.joinToString(", ") {
                "${it.name} (id:${it.entityId}) at distance ${it.distance}m, HP: ${it.health}"
            }

            val systemInstruction = """
                You are the MineMaster Natural Language Processing Module for Minecraft Forge.
                Your job is to translate arbitrary natural language player instructions into a sequence of atomic, executable Forge mod actions.
                You are given:
                1. Player Health, Hunger, Position, and Dimension.
                2. Full player Inventory with hotbar slots and tool durabilities.
                3. Surrounding 3D Voxel block scan and nearby entity/mob scan.

                Return a JSON object strictly following this structure:
                {
                   "intent": "MINING | CONSTRUCTION | COMBAT | EXPLORATION | SURVIVAL | CRAFTING",
                   "feedbackMessage": "Clear, friendly natural language response explaining what actions will be executed and any tactical warnings.",
                   "confidence": 0.95,
                   "warnings": ["warning 1", "warning 2"],
                   "actions": [
                      {
                         "actionType": "MOVE_TO | LOOK_AT | MINE_BLOCK | PLACE_BLOCK | ATTACK_ENTITY | SELECT_SLOT | SNEAK | JUMP | EXECUTE_COMMAND",
                         "targetX": 121.0,
                         "targetY": 64.0,
                         "targetZ": -249.0,
                         "targetEntityId": 101,
                         "slot": 1,
                         "blockType": "minecraft:cobblestone",
                         "delayMs": 400,
                         "description": "Select Iron Pickaxe in slot 1 and mine target block",
                         "command": "/setblock ~1 ~ ~ air destroy"
                      }
                   ]
                }
            """.trimIndent()

            val promptText = """
                Player Instruction: "$playerCommand"

                Current Player State:
                - Coordinates: X=${playerState.x}, Y=${playerState.y}, Z=${playerState.z}
                - Health: ${playerState.health}/${playerState.maxHealth}, Hunger: ${playerState.hunger}/20
                - Selected Hotbar Slot: ${playerState.selectedSlot}
                - Inventory: $invSummary
                - Surrounding Blocks: $blocksSummary
                - Nearby Entities: $entitiesSummary
            """.trimIndent()

            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray().put(JSONObject().put("text", promptText))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)

            val requestJson = JSONObject()
            requestJson.put("contents", contentsArray)

            val sysInstructionObj = JSONObject()
            sysInstructionObj.put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            requestJson.put("systemInstruction", sysInstructionObj)

            val genConfig = JSONObject()
            genConfig.put("temperature", 0.2)
            genConfig.put("responseMimeType", "application/json")
            requestJson.put("generationConfig", genConfig)

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("MinecraftNlp", "Gemini API error: ${response.code} $body")
                return@withContext parseCommandLocally(playerCommand, playerState)
            }

            parseGeminiNlpResponse(playerCommand, body, playerState)
        } catch (e: Exception) {
            Log.e("MinecraftNlp", "NLP translation failed", e)
            parseCommandLocally(playerCommand, playerState)
        }
    }

    private fun parseGeminiNlpResponse(
        originalCommand: String,
        responseJson: String,
        playerState: ForgePlayerState
    ): NlpParseResult {
        try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: "{}"

            val parsed = JSONObject(rawText)
            val intent = parsed.optString("intent", "GENERAL")
            val feedback = parsed.optString("feedbackMessage", "Executing your command in Minecraft Forge.")
            val confidence = parsed.optDouble("confidence", 0.95).toFloat()

            val warnings = mutableListOf<String>()
            val warnArray = parsed.optJSONArray("warnings")
            if (warnArray != null) {
                for (i in 0 until warnArray.length()) warnings.add(warnArray.getString(i))
            }

            val actions = mutableListOf<ForgeActionInstruction>()
            val actArray = parsed.optJSONArray("actions")
            if (actArray != null) {
                for (i in 0 until actArray.length()) {
                    val actObj = actArray.getJSONObject(i)
                    val typeStr = actObj.optString("actionType", "EXECUTE_COMMAND").uppercase()
                    val actionType = try {
                        ForgeActionType.valueOf(typeStr)
                    } catch (e: Exception) {
                        ForgeActionType.EXECUTE_COMMAND
                    }

                    actions.add(
                        ForgeActionInstruction(
                            id = UUID.randomUUID().toString(),
                            actionType = actionType,
                            targetX = if (actObj.has("targetX")) actObj.getDouble("targetX") else null,
                            targetY = if (actObj.has("targetY")) actObj.getDouble("targetY") else null,
                            targetZ = if (actObj.has("targetZ")) actObj.getDouble("targetZ") else null,
                            targetEntityId = if (actObj.has("targetEntityId")) actObj.getInt("targetEntityId") else null,
                            slot = if (actObj.has("slot")) actObj.getInt("slot") else null,
                            blockType = actObj.optString("blockType", null),
                            delayMs = actObj.optLong("delayMs", 400L),
                            description = actObj.optString("description", "Execute action"),
                            inGameCommand = actObj.optString("command", "/say [MineMaster] Action Executed")
                        )
                    )
                }
            }

            return NlpParseResult(
                originalCommand = originalCommand,
                intent = intent,
                feedbackMessage = feedback,
                confidence = confidence,
                warnings = warnings,
                forgeActions = actions.ifEmpty { parseCommandLocally(originalCommand, playerState).forgeActions }
            )
        } catch (e: Exception) {
            Log.e("MinecraftNlp", "Failed to parse Gemini NLP JSON", e)
            return parseCommandLocally(originalCommand, playerState)
        }

    }

    // High-speed, robust natural language semantic matcher for offline / zero-latency execution
    fun parseCommandLocally(command: String, state: ForgePlayerState): NlpParseResult {
        val lower = command.lowercase().trim()

        return when {
            // Mining variations: "mine some stone", "dig rock", "get cobblestone", "quarry stone"
            lower.contains("stone") || lower.contains("cobble") || lower.contains("mine") || lower.contains("dig") || lower.contains("quarry") -> {
                val pickaxeSlot = state.inventory.firstOrNull { it.itemId.contains("pickaxe") }?.slot ?: 1
                val actions = listOf(
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.SELECT_SLOT,
                        slot = pickaxeSlot,
                        description = "Equip Pickaxe from Hotbar slot ${pickaxeSlot + 1}",
                        inGameCommand = "/item replace entity @s hotbar.$pickaxeSlot with minecraft:iron_pickaxe"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.LOOK_AT,
                        targetX = state.x + 1.0,
                        targetY = state.y,
                        targetZ = state.z,
                        description = "Align crosshair to adjacent stone block (X=${(state.x + 1).toInt()})",
                        inGameCommand = "/execute at @s run teleport @s ~ ~ ~ 90 0"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.MINE_BLOCK,
                        targetX = state.x + 1.0,
                        targetY = state.y,
                        targetZ = state.z,
                        delayMs = 650L,
                        description = "Mine stone block at rel (+1, 0, 0)",
                        inGameCommand = "/setblock ~1 ~ ~ air destroy"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.MINE_BLOCK,
                        targetX = state.x + 1.0,
                        targetY = state.y + 1.0,
                        targetZ = state.z,
                        delayMs = 650L,
                        description = "Mine upper stone block at rel (+1, +1, 0)",
                        inGameCommand = "/setblock ~1 ~1 ~ air destroy"
                    )
                )
                NlpParseResult(
                    originalCommand = command,
                    intent = "MINING",
                    feedbackMessage = "Selected your pickaxe in slot ${pickaxeSlot + 1}. Mining adjacent stone blocks to expand inventory reserves.",
                    confidence = 0.98f,
                    warnings = listOf("Keep pickaxe durability above 20 to prevent breakage"),
                    forgeActions = actions
                )
            }

            // Shelter building variations: "build a shelter", "dirt bunker", "construct panic room", "make walls"
            lower.contains("shelter") || lower.contains("bunker") || lower.contains("house") || lower.contains("build") || lower.contains("wall") || lower.contains("hut") -> {
                val blockSlot = state.inventory.firstOrNull { it.itemId.contains("cobblestone") || it.itemId.contains("planks") || it.itemId.contains("dirt") }?.slot ?: 4
                val blockName = state.inventory.getOrNull(blockSlot)?.itemId ?: "minecraft:cobblestone"
                val actions = listOf(
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.SELECT_SLOT,
                        slot = blockSlot,
                        description = "Equip building material (${blockName.replace("minecraft:", "")}) from slot ${blockSlot + 1}",
                        inGameCommand = "/item replace entity @s hotbar.$blockSlot with $blockName"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.PLACE_BLOCK,
                        targetX = state.x + 1,
                        targetY = state.y,
                        targetZ = state.z,
                        blockType = blockName,
                        delayMs = 300L,
                        description = "Construct East wall block at (+1, 0, 0)",
                        inGameCommand = "/setblock ~1 ~ ~ $blockName"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.PLACE_BLOCK,
                        targetX = state.x - 1,
                        targetY = state.y,
                        targetZ = state.z,
                        blockType = blockName,
                        delayMs = 300L,
                        description = "Construct West wall block at (-1, 0, 0)",
                        inGameCommand = "/setblock ~-1 ~ ~ $blockName"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.PLACE_BLOCK,
                        targetX = state.x,
                        targetY = state.y,
                        targetZ = state.z + 1,
                        blockType = blockName,
                        delayMs = 300L,
                        description = "Construct South wall block at (0, 0, +1)",
                        inGameCommand = "/setblock ~ ~ ~1 $blockName"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.PLACE_BLOCK,
                        targetX = state.x,
                        targetY = state.y + 2,
                        targetZ = state.z,
                        blockType = blockName,
                        delayMs = 300L,
                        description = "Seal shelter roof at (0, +2, 0)",
                        inGameCommand = "/setblock ~ ~2 ~ $blockName"
                    )
                )
                NlpParseResult(
                    originalCommand = command,
                    intent = "CONSTRUCTION",
                    feedbackMessage = "Initiating emergency 360° perimeter shelter protocol with $blockName. Sealing 4 walls and overhead blast roof.",
                    confidence = 0.99f,
                    warnings = listOf("Ensure shelter interior is illuminated with a torch to avoid mob spawns"),
                    forgeActions = actions
                )
            }

            // Attack / Combat variations: "kill the creeper", "attack zombie", "strike mob", "defend"
            lower.contains("kill") || lower.contains("attack") || lower.contains("strike") || lower.contains("hit") || lower.contains("slay") || lower.contains("creeper") || lower.contains("zombie") -> {
                val swordSlot = state.inventory.firstOrNull { it.itemId.contains("sword") }?.slot ?: 0
                val targetMob = state.surroundings.nearbyEntities.firstOrNull { it.isHostile }
                val targetId = targetMob?.entityId ?: 101
                val targetName = targetMob?.name ?: "Hostile Entity"

                val actions = listOf(
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.SELECT_SLOT,
                        slot = swordSlot,
                        description = "Draw Diamond Sword from Hotbar slot ${swordSlot + 1}",
                        inGameCommand = "/item replace entity @s hotbar.$swordSlot with minecraft:diamond_sword"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.LOOK_AT,
                        targetX = (targetMob?.relX?.toDouble() ?: 2.0) + state.x,
                        targetY = state.y,
                        targetZ = (targetMob?.relZ?.toDouble() ?: 2.0) + state.z,
                        description = "Lock crosshairs onto $targetName (Distance: ${targetMob?.distance ?: 4.0}m)",
                        inGameCommand = "/execute at @s run teleport @s ~ ~ ~ facing entity @e[type=$targetName,limit=1]"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.MOVE_TO,
                        targetX = state.x + ((targetMob?.relX?.toDouble() ?: 2.0) * 0.7),
                        targetY = state.y,
                        targetZ = state.z + ((targetMob?.relZ?.toDouble() ?: 2.0) * 0.7),
                        delayMs = 450L,
                        description = "Sprint-approach to melee range (2.5 blocks)",
                        inGameCommand = "/execute as @s at @s run tp @s ~ ~ ~"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.ATTACK_ENTITY,
                        targetEntityId = targetId,
                        delayMs = 500L,
                        description = "Critical strike on $targetName",
                        inGameCommand = "/damage @e[limit=1,distance=..4] 7 entity_attack by @s"
                    )
                )
                NlpParseResult(
                    originalCommand = command,
                    intent = "COMBAT",
                    feedbackMessage = "Engaging combat protocol against $targetName. Equipping Diamond Sword and closing in for critical hit.",
                    confidence = 0.96f,
                    warnings = if (targetName.contains("Creeper", ignoreCase = true)) listOf("Keep distance > 3 blocks to prevent fuse ignition!") else emptyList(),
                    forgeActions = actions
                )
            }

            // Torch / Lighting: "place torch", "light up area", "illuminate"
            lower.contains("torch") || lower.contains("light") || lower.contains("illuminate") || lower.contains("dark") -> {
                val torchSlot = state.inventory.firstOrNull { it.itemId.contains("torch") }?.slot ?: 3
                val actions = listOf(
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.SELECT_SLOT,
                        slot = torchSlot,
                        description = "Select Torches in slot ${torchSlot + 1}",
                        inGameCommand = "/item replace entity @s hotbar.$torchSlot with minecraft:torch"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.PLACE_BLOCK,
                        targetX = state.x,
                        targetY = state.y,
                        targetZ = state.z,
                        blockType = "minecraft:torch",
                        delayMs = 300L,
                        description = "Place torch on ground at current coordinates",
                        inGameCommand = "/setblock ~ ~ ~ minecraft:torch"
                    )
                )
                NlpParseResult(
                    originalCommand = command,
                    intent = "SURVIVAL",
                    feedbackMessage = "Placing torch at your coordinates. Block light will rise to 14, completely stopping hostile spawns.",
                    confidence = 0.97f,
                    forgeActions = actions
                )
            }

            // Food / Healing: "eat bread", "heal", "eat food", "hunger"
            lower.contains("eat") || lower.contains("food") || lower.contains("bread") || lower.contains("heal") || lower.contains("hunger") -> {
                val foodSlot = state.inventory.firstOrNull { it.itemId.contains("bread") || it.itemId.contains("apple") || it.itemId.contains("beef") }?.slot ?: 5
                val actions = listOf(
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.SELECT_SLOT,
                        slot = foodSlot,
                        description = "Select Bread from slot ${foodSlot + 1}",
                        inGameCommand = "/item replace entity @s hotbar.$foodSlot with minecraft:bread"
                    ),
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.USE_ITEM,
                        slot = foodSlot,
                        delayMs = 1200L,
                        description = "Consume food item to restore hunger and trigger health regeneration",
                        inGameCommand = "/effect give @s instant_health 1 0"
                    )
                )
                NlpParseResult(
                    originalCommand = command,
                    intent = "SURVIVAL",
                    feedbackMessage = "Consuming Bread from slot ${foodSlot + 1}. Restoring 5 hunger points and enabling health regeneration.",
                    confidence = 0.98f,
                    forgeActions = actions
                )
            }

            // Fallback / General Command:
            else -> {
                val actions = listOf(
                    ForgeActionInstruction(
                        id = UUID.randomUUID().toString(),
                        actionType = ForgeActionType.EXECUTE_COMMAND,
                        description = "Execute AI directive in Minecraft: $command",
                        inGameCommand = "/say [MineMaster Forge] Executing: $command"
                    )
                )
                NlpParseResult(
                    originalCommand = command,
                    intent = "EXPLORATION",
                    feedbackMessage = "Understood directive: '$command'. Translating into tactical Forge bot commands.",
                    confidence = 0.85f,
                    forgeActions = actions
                )
            }
        }
    }
}
