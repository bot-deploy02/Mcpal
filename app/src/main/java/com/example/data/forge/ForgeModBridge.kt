package com.example.data.forge

import android.util.Log
import com.example.data.model.ForgeActionInstruction
import com.example.data.model.ForgeActionType
import com.example.data.model.ForgeBlockVoxel
import com.example.data.model.ForgeEntityNearby
import com.example.data.model.ForgeItemStack
import com.example.data.model.ForgePlayerState
import com.example.data.model.ForgeSurroundings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ForgeModBridge {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _hostAddress = MutableStateFlow("127.0.0.1")
    val hostAddress: StateFlow<String> = _hostAddress.asStateFlow()

    private val _port = MutableStateFlow(25585)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isSimulatedMode = MutableStateFlow(true)
    val isSimulatedMode: StateFlow<Boolean> = _isSimulatedMode.asStateFlow()

    private val _forgeState = MutableStateFlow(createInitialSimulatedState())
    val forgeState: StateFlow<ForgePlayerState> = _forgeState.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<String>>(emptyList())
    val executionLogs: StateFlow<List<String>> = _executionLogs.asStateFlow()

    fun updateConfig(host: String, port: Int, simulated: Boolean) {
        _hostAddress.value = host
        _port.value = port
        _isSimulatedMode.value = simulated
    }

    suspend fun syncWithForgeServer(): Boolean = withContext(Dispatchers.IO) {
        if (_isSimulatedMode.value) {
            _isConnected.value = true
            log("Forge Bridge: Active in Local Simulated Mode (Realistic World Voxel Engine).")
            return@withContext true
        }

        val url = "http://${_hostAddress.value}:${_port.value}/forge/telemetry"
        try {
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonString = response.body?.string() ?: ""
                parseForgeTelemetryJson(jsonString)
                _isConnected.value = true
                log("Forge Bridge: Connected to ${_hostAddress.value}:${_port.value}")
                true
            } else {
                _isConnected.value = false
                log("Forge Bridge: Connection failed (HTTP ${response.code}). Falling back to Simulation.")
                false
            }
        } catch (e: Exception) {
            _isConnected.value = false
            log("Forge Bridge: Server unreachable at $url. ${e.message}")
            false
        }
    }

    suspend fun dispatchAction(action: ForgeActionInstruction): Boolean = withContext(Dispatchers.IO) {
        log("Executing Forge Action [${action.actionType.label}]: ${action.description}")

        // Update local simulated state immediately for instantaneous visual feedback
        applySimulatedAction(action)

        if (_isSimulatedMode.value || !_isConnected.value) {
            return@withContext true
        }

        val url = "http://${_hostAddress.value}:${_port.value}/forge/execute"
        try {
            val json = JSONObject().apply {
                put("actionType", action.actionType.name)
                put("targetX", action.targetX)
                put("targetY", action.targetY)
                put("targetZ", action.targetZ)
                put("targetEntityId", action.targetEntityId)
                put("slot", action.slot)
                put("blockType", action.blockType)
                put("command", action.inGameCommand)
            }
            val body = json.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            log("Forge dispatch error: ${e.message}")
            false
        }
    }

    private fun applySimulatedAction(action: ForgeActionInstruction) {
        val current = _forgeState.value
        when (action.actionType) {
            ForgeActionType.MOVE_TO -> {
                val tx = action.targetX ?: current.x
                val ty = action.targetY ?: current.y
                val tz = action.targetZ ?: current.z
                _forgeState.value = current.copy(x = tx, y = ty, z = tz)
            }
            ForgeActionType.SELECT_SLOT -> {
                val slot = action.slot ?: 0
                _forgeState.value = current.copy(selectedSlot = slot)
            }
            ForgeActionType.MINE_BLOCK -> {
                // If mining stone, increment cobblestone count in inventory
                val updatedInv = current.inventory.map { item ->
                    if (item.itemId == "minecraft:cobblestone") {
                        item.copy(count = (item.count + 1).coerceAtMost(64))
                    } else if (item.itemId.contains("pickaxe")) {
                        item.copy(durability = (item.durability - 1).coerceAtLeast(0))
                    } else item
                }
                // Remove block from nearby surroundings
                val updatedBlocks = current.surroundings.nearbyBlocks.filterNot {
                    it.relX == (action.targetX?.toInt() ?: 0) &&
                    it.relY == (action.targetY?.toInt() ?: 0) &&
                    it.relZ == (action.targetZ?.toInt() ?: 1)
                }
                _forgeState.value = current.copy(
                    inventory = updatedInv,
                    surroundings = current.surroundings.copy(nearbyBlocks = updatedBlocks)
                )
            }
            ForgeActionType.PLACE_BLOCK -> {
                val updatedInv = current.inventory.map { item ->
                    if (item.itemId == "minecraft:cobblestone" || item.itemId == "minecraft:dirt") {
                        item.copy(count = (item.count - 1).coerceAtLeast(0))
                    } else item
                }
                val newBlock = ForgeBlockVoxel(
                    relX = action.targetX?.toInt() ?: 0,
                    relY = action.targetY?.toInt() ?: 0,
                    relZ = action.targetZ?.toInt() ?: 1,
                    blockName = action.blockType ?: "minecraft:cobblestone"
                )
                val updatedBlocks = current.surroundings.nearbyBlocks + newBlock
                _forgeState.value = current.copy(
                    inventory = updatedInv,
                    surroundings = current.surroundings.copy(nearbyBlocks = updatedBlocks)
                )
            }
            ForgeActionType.ATTACK_ENTITY -> {
                val targetId = action.targetEntityId
                val updatedMobs = current.surroundings.nearbyEntities.mapNotNull { mob ->
                    if (mob.entityId == targetId || targetId == null) {
                        val newHp = mob.health - 7f
                        if (newHp <= 0) null else mob.copy(health = newHp)
                    } else mob
                }
                _forgeState.value = current.copy(
                    surroundings = current.surroundings.copy(nearbyEntities = updatedMobs)
                )
            }
            else -> {}
        }
    }

    private fun parseForgeTelemetryJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val hp = root.optDouble("health", 20.0).toFloat()
            val maxHp = root.optDouble("maxHealth", 20.0).toFloat()
            val hunger = root.optInt("hunger", 20)
            val sat = root.optDouble("saturation", 10.0).toFloat()
            val x = root.optDouble("x", 0.0)
            val y = root.optDouble("y", 64.0)
            val z = root.optDouble("z", 0.0)
            val selected = root.optInt("selectedSlot", 0)

            val invList = mutableListOf<ForgeItemStack>()
            val invArray = root.optJSONArray("inventory")
            if (invArray != null) {
                for (i in 0 until invArray.length()) {
                    val itemObj = invArray.getJSONObject(i)
                    invList.add(
                        ForgeItemStack(
                            slot = itemObj.optInt("slot", i),
                            itemId = itemObj.optString("itemId", "minecraft:air"),
                            count = itemObj.optInt("count", 1),
                            durability = itemObj.optInt("durability", 100),
                            maxDurability = itemObj.optInt("maxDurability", 100),
                            displayName = itemObj.optString("displayName", itemObj.optString("itemId"))
                        )
                    )
                }
            }

            _forgeState.value = _forgeState.value.copy(
                health = hp,
                maxHealth = maxHp,
                hunger = hunger,
                saturation = sat,
                x = x,
                y = y,
                z = z,
                selectedSlot = selected,
                inventory = if (invList.isNotEmpty()) invList else _forgeState.value.inventory
            )
        } catch (e: Exception) {
            Log.e("ForgeModBridge", "Failed to parse telemetry JSON", e)
        }
    }

    private fun log(msg: String) {
        val list = _executionLogs.value.toMutableList()
        list.add(0, msg)
        if (list.size > 40) list.removeAt(list.size - 1)
        _executionLogs.value = list
    }

    companion object {
        fun createInitialSimulatedState(): ForgePlayerState {
            val inventory = listOf(
                ForgeItemStack(slot = 0, itemId = "minecraft:diamond_sword", count = 1, durability = 1420, maxDurability = 1561, displayName = "Diamond Sword"),
                ForgeItemStack(slot = 1, itemId = "minecraft:iron_pickaxe", count = 1, durability = 210, maxDurability = 250, displayName = "Iron Pickaxe"),
                ForgeItemStack(slot = 2, itemId = "minecraft:shield", count = 1, durability = 310, maxDurability = 336, displayName = "Shield"),
                ForgeItemStack(slot = 3, itemId = "minecraft:torch", count = 32, displayName = "Torch"),
                ForgeItemStack(slot = 4, itemId = "minecraft:cobblestone", count = 48, displayName = "Cobblestone"),
                ForgeItemStack(slot = 5, itemId = "minecraft:bread", count = 14, displayName = "Bread"),
                ForgeItemStack(slot = 6, itemId = "minecraft:water_bucket", count = 1, displayName = "Water Bucket"),
                ForgeItemStack(slot = 7, itemId = "minecraft:oak_planks", count = 64, displayName = "Oak Planks"),
                ForgeItemStack(slot = 8, itemId = "minecraft:iron_ingot", count = 8, displayName = "Iron Ingot")
            )

            val nearbyBlocks = listOf(
                ForgeBlockVoxel(relX = 0, relY = -1, relZ = 0, blockName = "minecraft:stone"),
                ForgeBlockVoxel(relX = 1, relY = 0, relZ = 0, blockName = "minecraft:stone"),
                ForgeBlockVoxel(relX = 0, relY = 0, relZ = 1, blockName = "minecraft:stone"),
                ForgeBlockVoxel(relX = -1, relY = 0, relZ = 0, blockName = "minecraft:iron_ore"),
                ForgeBlockVoxel(relX = 0, relY = 1, relZ = 2, blockName = "minecraft:stone"),
                ForgeBlockVoxel(relX = 2, relY = 0, relZ = 2, blockName = "minecraft:oak_log"),
                ForgeBlockVoxel(relX = -2, relY = -1, relZ = 2, blockName = "minecraft:dirt")
            )

            val nearbyEntities = listOf(
                ForgeEntityNearby(entityId = 101, name = "Creeper", type = "creeper", relX = 4.2f, relY = 0f, relZ = 6.1f, distance = 7.4f, health = 20f, isHostile = true),
                ForgeEntityNearby(entityId = 102, name = "Zombie", type = "zombie", relX = -3.1f, relY = 0f, relZ = 4.5f, distance = 5.4f, health = 20f, isHostile = true),
                ForgeEntityNearby(entityId = 103, name = "Sheep", type = "sheep", relX = 8.0f, relY = 0f, relZ = -2.0f, distance = 8.2f, health = 8f, isHostile = false)
            )

            return ForgePlayerState(
                health = 18f,
                maxHealth = 20f,
                hunger = 16,
                saturation = 8f,
                x = 124.5,
                y = 64.0,
                z = -312.2,
                yaw = -180.0f,
                pitch = 12.0f,
                selectedSlot = 1, // Iron pickaxe
                inventory = inventory,
                surroundings = ForgeSurroundings(
                    lightLevel = 9,
                    biome = "Plains",
                    nearbyBlocks = nearbyBlocks,
                    nearbyEntities = nearbyEntities
                )
            )
        }

        fun getForgeModJavaTemplate(): String {
            return """
                // --- Minecraft Forge 1.20+ Companion Bridge Mod ---
                // Save as: src/main/java/com/minemaster/forge/MineMasterBridgeMod.java
                package com.minemaster.forge;

                import net.minecraftforge.common.MinecraftForge;
                import net.minecraftforge.event.TickEvent;
                import net.minecraftforge.eventbus.api.SubscribeEvent;
                import net.minecraftforge.fml.common.Mod;
                import com.sun.net.httpserver.HttpServer;
                import java.net.InetSocketAddress;

                @Mod("minemaster_bridge")
                public class MineMasterBridgeMod {
                    public static final String MODID = "minemaster_bridge";
                    private HttpServer server;

                    public MineMasterBridgeMod() {
                        MinecraftForge.EVENT_BUS.register(this);
                        startHttpBridge(25585);
                    }

                    private void startHttpBridge(int port) {
                        try {
                            server = HttpServer.create(new InetSocketAddress(port), 0);
                            server.createContext("/forge/telemetry", (exchange) -> {
                                String json = TelemetrySerializer.getCurrentPlayerJson();
                                exchange.sendResponseHeaders(200, json.length());
                                exchange.getResponseBody().write(json.getBytes());
                                exchange.close();
                            });
                            server.createContext("/forge/execute", (exchange) -> {
                                // Executes actions: move, mine, place, attack
                                ActionExecutor.handleIncomingAction(exchange.getRequestBody());
                                String res = "{\"status\":\"OK\"}";
                                exchange.sendResponseHeaders(200, res.length());
                                exchange.getResponseBody().write(res.getBytes());
                                exchange.close();
                            });
                            server.start();
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            """.trimIndent()
        }
    }
}
