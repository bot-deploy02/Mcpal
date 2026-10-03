package com.example.data.advisor

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ActionPriority
import com.example.data.model.TacticalAction
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

enum class StrategyCategory(val label: String) {
    ALL("All Topics"),
    CRAFTING("Crafting & Recipes"),
    COMBAT_DEFENSE("Combat & Defense"),
    AUTOMATION_REDSTONE("Redstone & Automation"),
    SURVIVAL_MINING("Mining & Survival")
}

data class StrategyAdvice(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val category: StrategyCategory,
    val summary: String,
    val requiredMaterials: List<String>,
    val steps: List<String>,
    val proTips: List<String>,
    val hazardWarnings: List<String>,
    val actionDirectives: List<TacticalAction> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

class MinecraftStrategyAdvisor {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getAdvice(
        question: String,
        category: StrategyCategory = StrategyCategory.ALL
    ): StrategyAdvice = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d("StrategyAdvisor", "Using local high-speed strategy knowledge base (Offline/Demo mode)")
            return@withContext getOfflineAdvice(question, category)
        }

        try {
            val systemInstruction = """
                You are the ultimate Minecraft AI Strategy Advisor.
                Your task is to provide clear, concise, and highly actionable gameplay advice for Minecraft.
                Break down advice into:
                1. A punchy 1-2 sentence TL;DR summary.
                2. Exact materials/blocks/tools required.
                3. Sequential, numbered step-by-step instructions.
                4. Pro-gamer tips and tactical edge advice.
                5. Hazard warnings or common pitfalls.

                Return a JSON object strictly following this structure:
                {
                   "summary": "1-2 sentence concise answer",
                   "category": "CRAFTING | COMBAT_DEFENSE | AUTOMATION_REDSTONE | SURVIVAL_MINING",
                   "materials": ["Item 1", "Item 2"],
                   "steps": ["Step 1", "Step 2", "Step 3"],
                   "proTips": ["Tip 1", "Tip 2"],
                   "hazardWarnings": ["Warning 1"],
                   "inGameCommands": ["/give @s diamond_sword", "/setblock ~ ~ ~ redstone_wire"]
                }
            """.trimIndent()

            val promptText = "Minecraft Strategy Question: \"$question\" (Category filter: ${category.label})"

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
            genConfig.put("temperature", 0.3)
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
                Log.e("StrategyAdvisor", "Gemini API error: ${response.code} $body")
                return@withContext getOfflineAdvice(question, category)
            }

            parseGeminiAdviceResponse(question, category, body)
        } catch (e: Exception) {
            Log.e("StrategyAdvisor", "Error fetching strategy advice", e)
            getOfflineAdvice(question, category)
        }
    }

    private fun parseGeminiAdviceResponse(
        question: String,
        fallbackCategory: StrategyCategory,
        responseJson: String
    ): StrategyAdvice {
        try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: "{}"

            val parsed = JSONObject(rawText)
            val summary = parsed.optString("summary", "Strategy advice generated.")
            val catStr = parsed.optString("category", fallbackCategory.name).uppercase()
            val cat = when {
                catStr.contains("CRAFT") -> StrategyCategory.CRAFTING
                catStr.contains("COMBAT") || catStr.contains("DEFENSE") -> StrategyCategory.COMBAT_DEFENSE
                catStr.contains("AUTO") || catStr.contains("REDSTONE") -> StrategyCategory.AUTOMATION_REDSTONE
                else -> StrategyCategory.SURVIVAL_MINING
            }

            val materials = mutableListOf<String>()
            val matArray = parsed.optJSONArray("materials")
            if (matArray != null) {
                for (i in 0 until matArray.length()) materials.add(matArray.getString(i))
            }

            val steps = mutableListOf<String>()
            val stepArray = parsed.optJSONArray("steps")
            if (stepArray != null) {
                for (i in 0 until stepArray.length()) steps.add(stepArray.getString(i))
            }

            val proTips = mutableListOf<String>()
            val tipArray = parsed.optJSONArray("proTips")
            if (tipArray != null) {
                for (i in 0 until tipArray.length()) proTips.add(tipArray.getString(i))
            }

            val warnings = mutableListOf<String>()
            val warnArray = parsed.optJSONArray("hazardWarnings")
            if (warnArray != null) {
                for (i in 0 until warnArray.length()) warnings.add(warnArray.getString(i))
            }

            val commands = mutableListOf<String>()
            val cmdArray = parsed.optJSONArray("inGameCommands")
            if (cmdArray != null) {
                for (i in 0 until cmdArray.length()) commands.add(cmdArray.getString(i))
            }

            val actionItems = steps.mapIndexed { index, step ->
                TacticalAction(
                    id = UUID.randomUUID().toString(),
                    title = "Strategy Step ${index + 1}: ${step.take(30)}...",
                    description = step,
                    priority = if (cat == StrategyCategory.COMBAT_DEFENSE) ActionPriority.COMBAT else ActionPriority.SURVIVAL,
                    command = commands.getOrNull(index) ?: "/title @s actionbar §a[Advisor] Step ${index + 1} Active",
                    durationMs = 800L
                )
            }

            return StrategyAdvice(
                question = question,
                category = cat,
                summary = summary,
                requiredMaterials = materials,
                steps = steps,
                proTips = proTips,
                hazardWarnings = warnings,
                actionDirectives = actionItems
            )
        } catch (e: Exception) {
            Log.e("StrategyAdvisor", "Failed to parse advice JSON", e)
            return getOfflineAdvice(question, fallbackCategory)
        }
    }

    // High-speed, detailed offline Minecraft strategy knowledge base
    fun getOfflineAdvice(question: String, category: StrategyCategory): StrategyAdvice {
        val lower = question.lowercase().trim()

        return when {
            // Prompt example 1: "How do I craft a diamond sword?"
            lower.contains("diamond sword") || (lower.contains("craft") && lower.contains("sword")) -> {
                StrategyAdvice(
                    question = question,
                    category = StrategyCategory.CRAFTING,
                    summary = "To craft a Diamond Sword, arrange 1 Wooden Stick in the bottom-middle slot and 2 Diamonds vertically in the center and top-center slots of a 3x3 Crafting Table.",
                    requiredMaterials = listOf(
                        "1x Crafting Table",
                        "2x Diamonds (Found at Y=-58 in Deepslate)",
                        "1x Stick (Crafted from 2 Wooden Planks)"
                    ),
                    steps = listOf(
                        "Open a 3x3 Crafting Table grid.",
                        "Place 1 Stick in the bottom center slot (Row 3, Column 2).",
                        "Place 1 Diamond in the exact center slot (Row 2, Column 2).",
                        "Place 1 Diamond in the top center slot (Row 1, Column 2).",
                        "Collect your Diamond Sword from the output slot on the right."
                    ),
                    proTips = listOf(
                        "Base Damage: 7 Attack Damage (3.5 hearts) with 1.6 attack speed.",
                        "Top Priority Enchantments: Sharpness V (+3.5 damage), Unbreaking III, Looting III (more mob drops), and Mending.",
                        "Can later be upgraded into a Netherite Sword using a Smithing Table with a Netherite Upgrade Template and 1 Netherite Ingot."
                    ),
                    hazardWarnings = listOf(
                        "Never attack with a sword without letting the attack cooldown meter fill to 100%, otherwise damage is cut by up to 80%."
                    ),
                    actionDirectives = listOf(
                        TacticalAction(
                            id = UUID.randomUUID().toString(),
                            title = "Place Crafting Table",
                            description = "Deploy crafting table at player feet",
                            priority = ActionPriority.RESOURCE,
                            command = "/setblock ~ ~ ~ crafting_table",
                            durationMs = 600L
                        ),
                        TacticalAction(
                            id = UUID.randomUUID().toString(),
                            title = "Craft Diamond Sword",
                            description = "Assemble 2 diamonds + 1 stick recipe",
                            priority = ActionPriority.COMBAT,
                            command = "/give @s diamond_sword{Damage:0} 1",
                            durationMs = 800L,
                            hotbarSlot = 1
                        )
                    )
                )
            }

            // Prompt example 2: "What are the best ways to defend against creepers?"
            lower.contains("creeper") || (lower.contains("defend") && lower.contains("mob")) -> {
                StrategyAdvice(
                    question = question,
                    category = StrategyCategory.COMBAT_DEFENSE,
                    summary = "The #1 defense against Creepers is keeping a Shield in your offhand: holding Right-Click blocks 100% of blast and knockback damage. Alternatively, hit-and-sprint backward or use Ocelots/Cats which creepers actively flee from.",
                    requiredMaterials = listOf(
                        "1x Shield (Equipped in offhand slot)",
                        "1x Bow or Crossbow with Arrows (For ranged elimination)",
                        "1x Tamed Cat / Ocelot (Creepers flee within 6-16 blocks)",
                        "Torches (To keep surrounding block light level above 0)"
                    ),
                    steps = listOf(
                        "Equip your Shield in the Offhand slot: When a Creeper starts hissing, hold Sneak/Right-Click. The shield absorbs 100% of the explosion without heart loss.",
                        "Melee Hit-and-Reset technique: Sprint forward, hit the creeper to knock it back 3 blocks, then instantly backpedal to cancel its 1.5-second fuse timer.",
                        "Ranged Combat: Use a bow charged for 1 second. 2 fully charged shots eliminate a creeper before it enters detonation range (3 blocks).",
                        "Village & Base Defense: Tame a stray cat with raw cod/salmon and seat it at entrances. Creepers will run 16 blocks away in terror.",
                        "Lighting: Ensure all perimeter areas have block light ≥ 1, as creepers strictly require light level 0 to spawn."
                    ),
                    proTips = listOf(
                        "If you stand in water when a creeper detonates, the water completely nullifies terrain block damage!",
                        "Cats on house corners make your base 100% creeper-proof.",
                        "Charged Creepers (struck by lightning) have double the blast radius—eliminate only from long range with bows."
                    ),
                    hazardWarnings = listOf(
                        "Creepers make zero footstep sounds—only a 1.5-second hiss before explosion. Keep game audio or subtitles ON to see 'Creeper Hisses' warnings."
                    ),
                    actionDirectives = listOf(
                        TacticalAction(
                            id = UUID.randomUUID().toString(),
                            title = "Equip Offhand Shield",
                            description = "Block 100% explosion blast damage",
                            priority = ActionPriority.CRITICAL,
                            command = "/item replace entity @s slot.weapon.offhand with shield",
                            durationMs = 500L
                        ),
                        TacticalAction(
                            id = UUID.randomUUID().toString(),
                            title = "Deploy Water Buffer",
                            description = "Pour water bucket to cancel terrain explosion",
                            priority = ActionPriority.SURVIVAL,
                            command = "/setblock ~ ~ ~ water",
                            durationMs = 600L,
                            hotbarSlot = 4
                        )
                    )
                )
            }

            // Prompt example 3: "Explain how to build a simple automated farm."
            lower.contains("farm") || lower.contains("automated") || lower.contains("sugarcane") || lower.contains("piston") -> {
                StrategyAdvice(
                    question = question,
                    category = StrategyCategory.AUTOMATION_REDSTONE,
                    summary = "The easiest automated farm is a Zero-Loss Automatic Sugarcane / Bamboo Farm using Pistons, Observers, and a Hopper Minecart. When the cane grows 3 blocks tall, the observer triggers the piston to break it into a water collection stream.",
                    requiredMaterials = listOf(
                        "8x Sugarcane or Bamboo stalks",
                        "8x Dirt, Grass, or Sand blocks with adjacent Water",
                        "8x Regular Pistons (Facing the cane at height 2)",
                        "8x Observers (Placed on top of pistons facing the cane at height 3)",
                        "8x Redstone Dust (Placed on solid blocks behind pistons)",
                        "1x Chest + 1x Hopper (For automatic storage collection)"
                    ),
                    steps = listOf(
                        "Dig an 8-block trench, place a water source block at one end so it flows 8 blocks, and place a Hopper at the end leading into a Chest.",
                        "Plant 8 Sugarcane stalks on dirt or sand directly adjacent to the water stream.",
                        "Behind the sugarcane, place a row of solid blocks (height 1) and place 8 Pistons on top of them (height 2) facing toward the sugarcane.",
                        "Place 8 Observers on top of the pistons (height 3) with their red 'eye' sensors facing the sugarcane.",
                        "Place a row of solid blocks directly behind the pistons, and put 1 Redstone Dust on each block behind the observers.",
                        "How it Works: When sugarcane grows to block height 3, the Observer detects block update, powers the redstone dust, fires the piston, snaps the top 2 cane stalks into the water stream, and flows into your chest!"
                    ),
                    proTips = listOf(
                        "Glass walls in front of the farm prevent harvested sugarcane from bouncing onto the dirt.",
                        "A single 8-block module produces ~40 sugarcane per hour without any player interaction or bonemeal.",
                        "The exact same design works for 100% automated Bamboo and Kelp fuel farms."
                    ),
                    hazardWarnings = listOf(
                        "Do NOT place the pistons at ground level (height 1)—always place them at height 2 so the root sugarcane remains planted to regrow!"
                    ),
                    actionDirectives = listOf(
                        TacticalAction(
                            id = UUID.randomUUID().toString(),
                            title = "Place Farm Water Trench",
                            description = "Dig 8 block trench and drop water",
                            priority = ActionPriority.RESOURCE,
                            command = "/fill ~ ~-1 ~ ~7 ~-1 ~ water",
                            durationMs = 900L
                        ),
                        TacticalAction(
                            id = UUID.randomUUID().toString(),
                            title = "Deploy Observer & Piston Grid",
                            description = "Align automated harvest triggers",
                            priority = ActionPriority.RESOURCE,
                            command = "/title @s actionbar §a[Farm] Piston-Observer Circuit Ready",
                            durationMs = 1200L
                        )
                    )
                )
            }

            // Fallback for general Minecraft strategy queries
            else -> {
                StrategyAdvice(
                    question = question,
                    category = category,
                    summary = "Tactical strategy analysis for: '$question'. Follow standard Minecraft progression protocols to maximize efficiency and minimize mortality.",
                    requiredMaterials = listOf(
                        "Full Iron Armor set (Defense 15)",
                        "Shield (Offhand slot)",
                        "Diamond Pickaxe (Fortune III or Silk Touch)",
                        "64x Torches (Spaced 6 blocks apart)",
                        "Water Bucket (Climbing, MLG falls, and lava cooling)"
                    ),
                    steps = listOf(
                        "Establish safe base perimeter: Secure a 32x32 perimeter with light level ≥ 1 to prevent hostile mob generation.",
                        "Resource Tier Progression: Progress sequentially: Wood -> Stone -> Iron -> Diamond (Y=-58) -> Nether (Blaze Rods/Ender Pearls) -> Netherite -> The End.",
                        "Always carry an 'Escape Hotbar': Slot 1 Weapon, Slot 2 Pickaxe, Slot 3 Torches, Slot 4 Water Bucket, Slot 5 Food, Slot 9 Shield.",
                        "Consult tactical depth heatmaps: For diamonds, mine strictly at Y=-58 in Deepslate caverns."
                    ),
                    proTips = listOf(
                        "Never dig straight down or straight up—always stand on the border of two blocks and alternate mining.",
                        "Sleeping in a bed at sunset resets the Phantom spawn timer (requires 3 sleepless in-game days)."
                    ),
                    hazardWarnings = listOf(
                        "Subterranean lava lakes generate at Y=-54. Maintain water bucket in hotbar at all times."
                    )
                )
            }
        }
    }
}
