package com.example.data.gemini

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ActionPriority
import com.example.data.model.ActionStatus
import com.example.data.model.PlayerTelemetry
import com.example.data.model.TacticalAction
import com.example.data.model.ThreatSeverity
import com.example.data.model.VisionScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeGameScreen(
        bitmap: Bitmap?,
        telemetry: PlayerTelemetry,
        customPrompt: String = ""
    ): VisionScanResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("GeminiService", "API key missing or placeholder. Using tactical offline simulator.")
            return@withContext getSimulatedVisionResult(bitmap, telemetry)
        }

        try {
            val systemInstruction = """
                You are MineMaster AI, an expert tactical game copilot and Game Master operating live over Minecraft.
                Analyze the gameplay screenshot and player state.
                Return a JSON object strictly following this structure:
                {
                   "summary": "1-2 sentence immediate tactical assessment",
                   "biome": "Detected biome or environment",
                   "healthEstimate": "e.g. 18/20 HP, 8.5 hearts",
                   "threatLevel": "LOW | MEDIUM | HIGH | FATAL",
                   "entities": ["list", "of", "detected", "mobs", "or", "hazards"],
                   "directives": ["Immediate directive 1", "Immediate directive 2"],
                   "actions": [
                      {
                         "title": "Action title",
                         "description": "Short explanation",
                         "priority": "CRITICAL | COMBAT | SURVIVAL | RESOURCE | NAVIGATION",
                         "command": "Minecraft /command or macro syntax",
                         "durationMs": 1000,
                         "hotbarSlot": 1
                      }
                   ]
                }
            """.trimIndent()

            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            val promptText = if (customPrompt.isNotBlank()) {
                "User query: $customPrompt\nCurrent player state: Y=${telemetry.y}, Biome=${telemetry.biome}, Light=${telemetry.lightLevel}, Dimension=${telemetry.dimension.label}."
            } else {
                "Analyze this Minecraft frame. Detect hostiles, inventory weaknesses, blocks, and generate high-speed action queue directives. Current player state: Y=${telemetry.y}, Light=${telemetry.lightLevel}, Dimension=${telemetry.dimension.label}."
            }

            val textPart = JSONObject()
            textPart.put("text", promptText)
            partsArray.put(textPart)

            if (bitmap != null) {
                val imagePart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", bitmap.toBase64())
                imagePart.put("inlineData", inlineData)
                partsArray.put(imagePart)
            }

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)

            val requestJson = JSONObject()
            requestJson.put("contents", contentsArray)

            val sysInstructionObj = JSONObject()
            val sysParts = JSONArray()
            val sysPart = JSONObject().put("text", systemInstruction)
            sysParts.put(sysPart)
            sysInstructionObj.put("parts", sysParts)
            requestJson.put("systemInstruction", sysInstructionObj)

            val generationConfig = JSONObject()
            generationConfig.put("temperature", 0.4)
            generationConfig.put("responseMimeType", "application/json")
            requestJson.put("generationConfig", generationConfig)

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiService", "API failed code=${response.code}, body=$responseBody")
                return@withContext getSimulatedVisionResult(bitmap, telemetry)
            }

            parseVisionResponse(responseBody, telemetry)
        } catch (e: Exception) {
            Log.e("GeminiService", "Network or parse error in analyzeGameScreen", e)
            getSimulatedVisionResult(bitmap, telemetry)
        }
    }

    suspend fun generateGameMasterQuest(
        tier: String,
        playerTelemetry: PlayerTelemetry
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "The Ancient Echo Whispers: You stand at Y=${playerTelemetry.y.toInt()} in the ${playerTelemetry.biome}. The stars align over day ${playerTelemetry.gameDay}. Delve deep into the cavern depths to awaken the dormant redstone pulses and uncover the relics of the ancients."
        }

        try {
            val prompt = "You are the ancient Game Master of Minecraft. Formulate an atmospheric, lore-rich quest briefing for a player currently in ${playerTelemetry.dimension.label} at Y=${playerTelemetry.y.toInt()} in biome ${playerTelemetry.biome}. Progression tier: $tier. Keep it exciting, tactical, and 2-3 paragraphs with clear in-game objectives."

            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray().put(JSONObject().put("text", prompt))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)

            val requestJson = JSONObject().put("contents", contentsArray)
            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder().url(endpoint).post(requestBody).build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            text ?: "The spirits of the Minecraft realm remain watchful. Continue your journey."
        } catch (e: Exception) {
            "The Ancient Echo Whispers: The deep subterranean pressure mounts at Y=${playerTelemetry.y.toInt()}. Beware the shadows in the dark."
        }
    }

    private fun parseVisionResponse(responseBody: String, telemetry: PlayerTelemetry): VisionScanResult {
        try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: "{}"

            val parsedJson = JSONObject(rawText)
            val summary = parsedJson.optString("summary", "Tactical assessment completed.")
            val biome = parsedJson.optString("biome", telemetry.biome)
            val healthEst = parsedJson.optString("healthEstimate", "${telemetry.health}/20 HP")
            val threatStr = parsedJson.optString("threatLevel", "MEDIUM").uppercase()
            val threatLevel = when {
                threatStr.contains("FATAL") -> ThreatSeverity.FATAL
                threatStr.contains("HIGH") -> ThreatSeverity.HIGH
                threatStr.contains("MEDIUM") -> ThreatSeverity.MEDIUM
                else -> ThreatSeverity.LOW
            }

            val entities = mutableListOf<String>()
            val entitiesArray = parsedJson.optJSONArray("entities")
            if (entitiesArray != null) {
                for (i in 0 until entitiesArray.length()) {
                    entities.add(entitiesArray.getString(i))
                }
            }

            val directives = mutableListOf<String>()
            val dirArray = parsedJson.optJSONArray("directives")
            if (dirArray != null) {
                for (i in 0 until dirArray.length()) {
                    directives.add(dirArray.getString(i))
                }
            }

            val actions = mutableListOf<TacticalAction>()
            val actArray = parsedJson.optJSONArray("actions")
            if (actArray != null) {
                for (i in 0 until actArray.length()) {
                    val obj = actArray.getJSONObject(i)
                    val pStr = obj.optString("priority", "SURVIVAL").uppercase()
                    val priority = when {
                        pStr.contains("CRITICAL") -> ActionPriority.CRITICAL
                        pStr.contains("COMBAT") -> ActionPriority.COMBAT
                        pStr.contains("RESOURCE") -> ActionPriority.RESOURCE
                        pStr.contains("NAVIGATION") -> ActionPriority.NAVIGATION
                        else -> ActionPriority.SURVIVAL
                    }
                    actions.add(
                        TacticalAction(
                            id = UUID.randomUUID().toString(),
                            title = obj.optString("title", "Tactical Directive"),
                            description = obj.optString("description", ""),
                            priority = priority,
                            status = ActionStatus.PENDING,
                            command = obj.optString("command", "/say [MineMaster] Action Executed"),
                            durationMs = obj.optLong("durationMs", 1000L),
                            hotbarSlot = if (obj.has("hotbarSlot")) obj.getInt("hotbarSlot") else null
                        )
                    )
                }
            }

            if (actions.isEmpty()) {
                actions.addAll(getDefaultActionsForBiome(biome, telemetry.y))
            }

            return VisionScanResult(
                summary = summary,
                biomeDetected = biome,
                healthEstimate = healthEst,
                threatLevel = threatLevel,
                detectedEntities = entities.ifEmpty { listOf("Passive terrain", "Subterranean cavern") },
                tacticalDirectives = directives.ifEmpty { listOf("Maintain high light level > 0", "Guard offhand shield") },
                recommendedActions = actions
            )
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to parse vision response", e)
            return getSimulatedVisionResult(null, telemetry)
        }
    }

    private fun getSimulatedVisionResult(bitmap: Bitmap?, telemetry: PlayerTelemetry): VisionScanResult {
        val y = telemetry.y.toInt()
        val isDeep = y < -20
        val isNether = telemetry.dimension == com.example.data.model.Dimension.NETHER

        val threat = when {
            isNether -> ThreatSeverity.HIGH
            isDeep && telemetry.lightLevel < 2 -> ThreatSeverity.FATAL
            telemetry.lightLevel == 0 -> ThreatSeverity.HIGH
            else -> ThreatSeverity.MEDIUM
        }

        val entities = if (isNether) {
            listOf("Blaze (x2)", "Wither Skeleton", "Lava fall (3 blocks East)")
        } else if (isDeep) {
            listOf("Sculk Shrieker (Warning level 2/3)", "Creeper (Blind corner)", "Bat")
        } else {
            listOf("Skeleton (Bow drew)", "Zombie", "Iron Ore Vein (Y=$y)")
        }

        val directives = if (isNether) {
            listOf(
                "Drink Fire Resistance Potion immediately",
                "Shield up to deflect blaze fireballs",
                "Place cobblestone barricade against Ghasts"
            )
        } else if (isDeep) {
            listOf(
                "Hold SNEAK (Crouch) to silence footsteps",
                "Do NOT break blocks within 8 blocks of Shrieker",
                "Place wool path toward escape tunnel"
            )
        } else {
            listOf(
                "Raise Shield against incoming skeleton arrow",
                "Drop torches on right wall every 6 blocks",
                "Branch mine at Y=-58 for optimal diamond yield"
            )
        }

        return VisionScanResult(
            summary = "MineMaster Vision scan: Threat level ${threat.label}. Hostile signatures detected nearby. High-speed action queue updated.",
            biomeDetected = if (isNether) "Nether Wastes / Basalt Deltas" else if (isDeep) "Deep Dark / Dripstone Caves" else telemetry.biome,
            healthEstimate = "${telemetry.health}/20 HP",
            threatLevel = threat,
            detectedEntities = entities,
            tacticalDirectives = directives,
            recommendedActions = getDefaultActionsForBiome(telemetry.biome, telemetry.y)
        )
    }

    fun getDefaultActionsForBiome(biome: String, y: Double): List<TacticalAction> {
        val list = mutableListOf<TacticalAction>()
        if (y < 0) {
            list.add(
                TacticalAction(
                    id = UUID.randomUUID().toString(),
                    title = "Equip Offhand Shield",
                    description = "Deploy shield to block skeleton arrows and blast radius.",
                    priority = ActionPriority.CRITICAL,
                    status = ActionStatus.PENDING,
                    command = "/replaceitem entity @s slot.weapon.offhand 0 shield",
                    durationMs = 600L,
                    hotbarSlot = 1
                )
            )
            list.add(
                TacticalAction(
                    id = UUID.randomUUID().toString(),
                    title = "Optimal Diamond Strip Mine",
                    description = "Dig 2x1 tunnel heading North at Y=-58; skip water pockets.",
                    priority = ActionPriority.RESOURCE,
                    status = ActionStatus.PENDING,
                    command = "/title @s actionbar §b[MineMaster] Mining Y=-58 Vector",
                    durationMs = 1200L,
                    hotbarSlot = 2
                )
            )
            list.add(
                TacticalAction(
                    id = UUID.randomUUID().toString(),
                    title = "Drop Perimeter Torch",
                    description = "Raise block light level above 0 to prevent mob spawns.",
                    priority = ActionPriority.SURVIVAL,
                    status = ActionStatus.PENDING,
                    command = "/setblock ~ ~ ~ torch",
                    durationMs = 800L,
                    hotbarSlot = 3
                )
            )
            list.add(
                TacticalAction(
                    id = UUID.randomUUID().toString(),
                    title = "Water Bucket Cushion Ready",
                    description = "Keep water bucket in hotbar slot 4 for lava cooling and MLG landings.",
                    priority = ActionPriority.COMBAT,
                    status = ActionStatus.PENDING,
                    command = "/title @s actionbar §e[Hotbar] Water Bucket Selected",
                    durationMs = 700L,
                    hotbarSlot = 4
                )
            )
        } else {
            list.add(
                TacticalAction(
                    id = UUID.randomUUID().toString(),
                    title = "Nightfall Shelter Bunker",
                    description = "Seal a 3x3 dirt or cobblestone shelter with a roof before sunset.",
                    priority = ActionPriority.SURVIVAL,
                    status = ActionStatus.PENDING,
                    command = "/title @s actionbar §c[Alert] Sunset Imminent - Fortify",
                    durationMs = 900L,
                    hotbarSlot = 1
                )
            )
            list.add(
                TacticalAction(
                    id = UUID.randomUUID().toString(),
                    title = "Iron Golem Alarm Trigger",
                    description = "Gather 4 iron blocks and 1 carved pumpkin for village defense.",
                    priority = ActionPriority.COMBAT,
                    status = ActionStatus.PENDING,
                    command = "/tellraw @a {\"text\":\"[MineMaster] Defense Golem Ready\"}",
                    durationMs = 1500L,
                    hotbarSlot = 5
                )
            )
        }
        return list
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
