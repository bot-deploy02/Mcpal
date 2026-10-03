package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.gemini.GeminiService
import com.example.data.model.ActionPriority
import com.example.data.model.ActionStatus
import com.example.data.model.Dimension
import com.example.data.model.GameMasterQuest
import com.example.data.model.PlayerTelemetry
import com.example.data.model.TacticalAction
import com.example.data.model.ThreatBlip
import com.example.data.model.ThreatSeverity
import com.example.data.model.VisionScanResult
import com.example.data.advisor.MinecraftStrategyAdvisor
import com.example.data.advisor.StrategyAdvice
import com.example.data.advisor.StrategyCategory
import com.example.data.forge.ForgeModBridge
import com.example.data.model.ForgeActionInstruction
import com.example.data.model.ForgeActionType
import com.example.data.model.MultiplayerThreat
import com.example.data.model.NlpParseResult
import com.example.data.model.RadarDisplayMode
import com.example.data.model.TeamMember
import com.example.data.nlp.MinecraftNlpModule
import com.example.data.service.BedrockWebSocketBridge
import com.example.service.OverlayBridge
import com.example.ui.components.ScenarioPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CopilotViewModel(application: Application) : AndroidViewModel(application) {

    private val geminiService = GeminiService()
    val bedrockBridge = BedrockWebSocketBridge()
    val forgeBridge = ForgeModBridge()
    val nlpModule = MinecraftNlpModule()
    val strategyAdvisor = MinecraftStrategyAdvisor()
    private val vibrator = application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val _nlpHistory = MutableStateFlow<List<NlpParseResult>>(emptyList())
    val nlpHistory: StateFlow<List<NlpParseResult>> = _nlpHistory.asStateFlow()

    private val _latestNlpResult = MutableStateFlow<NlpParseResult?>(null)
    val latestNlpResult: StateFlow<NlpParseResult?> = _latestNlpResult.asStateFlow()

    private val _isNlpProcessing = MutableStateFlow(false)
    val isNlpProcessing: StateFlow<Boolean> = _isNlpProcessing.asStateFlow()

    private val _advisorCurrentAdvice = MutableStateFlow<StrategyAdvice?>(null)
    val advisorCurrentAdvice: StateFlow<StrategyAdvice?> = _advisorCurrentAdvice.asStateFlow()

    private val _isAdvisorLoading = MutableStateFlow(false)
    val isAdvisorLoading: StateFlow<Boolean> = _isAdvisorLoading.asStateFlow()

    private val _advisorHistory = MutableStateFlow<List<StrategyAdvice>>(emptyList())
    val advisorHistory: StateFlow<List<StrategyAdvice>> = _advisorHistory.asStateFlow()

    private val _teamMembers = MutableStateFlow<List<TeamMember>>(emptyList())
    val teamMembers: StateFlow<List<TeamMember>> = _teamMembers.asStateFlow()

    private val _multiplayerThreats = MutableStateFlow<List<MultiplayerThreat>>(emptyList())
    val multiplayerThreats: StateFlow<List<MultiplayerThreat>> = _multiplayerThreats.asStateFlow()

    private val _radarMode = MutableStateFlow(RadarDisplayMode.ALL_ENTITIES)
    val radarMode: StateFlow<RadarDisplayMode> = _radarMode.asStateFlow()

    private val _isAutoDispatchActive = MutableStateFlow(true)
    val isAutoDispatchActive: StateFlow<Boolean> = _isAutoDispatchActive.asStateFlow()

    private val _playerTelemetry = MutableStateFlow(
        PlayerTelemetry(
            health = 18,
            maxHealth = 20,
            hunger = 15,
            armor = 16,
            biome = "Deepslate Caves",
            dimension = Dimension.OVERWORLD,
            x = 142.4,
            y = -58.0,
            z = -889.1,
            lightLevel = 3,
            gameDay = 45,
            timeOfDay = "Night (23:10)"
        )
    )
    val playerTelemetry: StateFlow<PlayerTelemetry> = _playerTelemetry.asStateFlow()

    private val _actionQueue = MutableStateFlow<List<TacticalAction>>(emptyList())
    val actionQueue: StateFlow<List<TacticalAction>> = _actionQueue.asStateFlow()

    private val _isAutoQueueRunning = MutableStateFlow(true)
    val isAutoQueueRunning: StateFlow<Boolean> = _isAutoQueueRunning.asStateFlow()

    private val _queueSpeedMs = MutableStateFlow(1000L) // Normal speed
    val queueSpeedMs: StateFlow<Long> = _queueSpeedMs.asStateFlow()

    private val _threatRadarBlips = MutableStateFlow<List<ThreatBlip>>(emptyList())
    val threatRadarBlips: StateFlow<List<ThreatBlip>> = _threatRadarBlips.asStateFlow()

    private val _selectedThreat = MutableStateFlow<ThreatBlip?>(null)
    val selectedThreat: StateFlow<ThreatBlip?> = _selectedThreat.asStateFlow()

    private val _selectedDepthY = MutableStateFlow(-58)
    val selectedDepthY: StateFlow<Int> = _selectedDepthY.asStateFlow()

    private val _gameMasterQuests = MutableStateFlow<List<GameMasterQuest>>(emptyList())
    val gameMasterQuests: StateFlow<List<GameMasterQuest>> = _gameMasterQuests.asStateFlow()

    private val _gameMasterDialogue = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val gameMasterDialogue: StateFlow<List<Pair<String, String>>> = _gameMasterDialogue.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanResult = MutableStateFlow<VisionScanResult?>(null)
    val lastScanResult: StateFlow<VisionScanResult?> = _lastScanResult.asStateFlow()

    private var queueExecutionJob: Job? = null

    init {
        initializeInitialState()
        startQueueLoop()
        setupOverlaySync()
    }

    private fun initializeInitialState() {
        val defaultActions = geminiService.getDefaultActionsForBiome("Deepslate Caves", -58.0)
        _actionQueue.value = defaultActions

        _threatRadarBlips.value = listOf(
            ThreatBlip(
                id = "1",
                name = "Creeper",
                type = "Creeper",
                distanceBlocks = 14f,
                angleDegrees = 45f,
                severity = ThreatSeverity.HIGH,
                tactic = "Flanking on right ledge! Deploy shield and sprint-crit backward."
            ),
            ThreatBlip(
                id = "2",
                name = "Skeleton Archer",
                type = "Skeleton",
                distanceBlocks = 28f,
                angleDegrees = 190f,
                severity = ThreatSeverity.MEDIUM,
                tactic = "Sniper in dark corridor. Place cobblestone pillar for cover."
            ),
            ThreatBlip(
                id = "3",
                name = "Sculk Shrieker",
                type = "Warden Sensor",
                distanceBlocks = 42f,
                angleDegrees = 270f,
                severity = ThreatSeverity.FATAL,
                tactic = "Warning count 2/3! Sneak strictly on wool to prevent Warden summon."
            )
        )

        _teamMembers.value = listOf(
            TeamMember(
                id = "tm1",
                name = "Alex_Miner",
                role = "Scout",
                health = 20,
                maxHealth = 20,
                x = 138.5,
                y = -58.0,
                z = -875.0,
                distanceBlocks = 14f,
                angleDegrees = 45f,
                pingStatus = "Mining iron vein",
                isBeaconActive = true,
                holdingItem = "Iron Pickaxe"
            ),
            TeamMember(
                id = "tm2",
                name = "Steve_Warrior",
                role = "Tank",
                health = 17,
                maxHealth = 20,
                x = 120.0,
                y = -56.0,
                z = -910.0,
                distanceBlocks = 28f,
                angleDegrees = 220f,
                pingStatus = "Covering rear perimeter",
                isBeaconActive = false,
                holdingItem = "Diamond Sword"
            ),
            TeamMember(
                id = "tm3",
                name = "EnderPro_99",
                role = "Builder",
                health = 20,
                maxHealth = 20,
                x = 165.0,
                y = -52.0,
                z = -880.0,
                distanceBlocks = 36f,
                angleDegrees = 110f,
                pingStatus = "Fortifying bunker",
                isBeaconActive = false,
                holdingItem = "Oak Planks"
            )
        )

        _multiplayerThreats.value = listOf(
            MultiplayerThreat(
                id = "pvp1",
                playerName = "DreadLord_X",
                distanceBlocks = 24f,
                angleDegrees = 310f,
                armorTier = "Full Enchanted Netherite",
                weapon = "Sharpness V Netherite Sword",
                velocityDesc = "Sprinting toward squad position",
                severity = ThreatSeverity.FATAL,
                tactic = "Equip offhand shield immediately. Kite with power bow and coordinate crossfire with Steve_Warrior!"
            )
        )

        _gameMasterQuests.value = listOf(

            GameMasterQuest(
                id = "q1",
                title = "The Obsidian Bastion & Nether Gateway",
                tier = "Tier IV: Nether Incursion",
                lore = "The ancient texts speak of twin obsidian gates connecting the mortal Overworld to the crimson furnace of the Nether. Ten blocks of hardened tear-stone will pierce the dimensional veil.",
                objectives = listOf(
                    "Mine 10 Obsidian blocks with Diamond Pickaxe",
                    "Craft Flint & Steel from gravel and iron ingot",
                    "Ignite the 4x5 nether portal frame"
                ),
                rewards = listOf("Netherite Compass", "Fire Resistance II Potion", "+500 XP")
            ),
            GameMasterQuest(
                id = "q2",
                title = "Subterranean Secrets of the Deep Dark",
                tier = "Tier V: Ancient Echoes",
                lore = "Deep below Y=-50 lies the sunken necropolis of the ancients. Beware the vibrations that awaken the blind sovereign.",
                objectives = listOf(
                    "Explore Deepslate Cavern below Y=-52",
                    "Disarm 3 Sculk Catalysts using shears or hoe",
                    "Loot an Ancient City chest without triggering shrieker"
                ),
                rewards = listOf("Echo Shard x3", "Swift Sneak III Book", "+1200 XP")
            )
        )

        _gameMasterDialogue.value = listOf(
            "Game Master" to "Greetings, Champion of the Blocks. I am the Ancient Watcher, your Gemini Copilot. I analyze your world depth, hostile frequencies, and survival matrices. What task do you set before us?"
        )

        val defaultAdvice = strategyAdvisor.getOfflineAdvice(
            "How do I craft a diamond sword?",
            StrategyCategory.CRAFTING
        )
        _advisorCurrentAdvice.value = defaultAdvice
        _advisorHistory.value = listOf(defaultAdvice)
    }


    private fun setupOverlaySync() {
        OverlayBridge.onExecuteTopAction.value = {
            stepNextAction()
        }
        OverlayBridge.onScanTriggered.value = {
            runVisionScan(null, "Emergency scan from floating overlay", null)
        }

        viewModelScope.launch {
            actionQueue.collect { list ->
                val top = list.firstOrNull { it.status == ActionStatus.PENDING || it.status == ActionStatus.EXECUTING }
                OverlayBridge.currentTopAction.value = top
            }
        }

        viewModelScope.launch {
            threatRadarBlips.collect { blips ->
                val fatal = blips.firstOrNull { it.severity == ThreatSeverity.FATAL }
                val high = blips.firstOrNull { it.severity == ThreatSeverity.HIGH }
                if (fatal != null) {
                    OverlayBridge.threatText.value = "FATAL: ${fatal.name} (${fatal.distanceBlocks.toInt()}m)"
                    OverlayBridge.threatColor.value = ThreatSeverity.FATAL.color
                } else if (high != null) {
                    OverlayBridge.threatText.value = "ALERT: ${high.name} (${high.distanceBlocks.toInt()}m)"
                    OverlayBridge.threatColor.value = ThreatSeverity.HIGH.color
                } else {
                    OverlayBridge.threatText.value = "THREAT: Scanner Clear (Y=${_playerTelemetry.value.y.toInt()})"
                    OverlayBridge.threatColor.value = ThreatSeverity.LOW.color
                }
            }
        }
    }

    private fun startQueueLoop() {
        queueExecutionJob?.cancel()
        queueExecutionJob = viewModelScope.launch {
            while (true) {
                val delayTime = _queueSpeedMs.value
                delay(delayTime)

                if (!_isAutoQueueRunning.value) continue

                val currentList = _actionQueue.value
                val firstPendingIndex = currentList.indexOfFirst { it.status == ActionStatus.PENDING }

                if (firstPendingIndex != -1) {
                    // Start executing
                    val executingList = currentList.toMutableList()
                    val target = executingList[firstPendingIndex]
                    executingList[firstPendingIndex] = target.copy(status = ActionStatus.EXECUTING, progress = 0.2f)
                    _actionQueue.value = executingList

                    // Broadcast command to Minecraft Bedrock bridge if connected!
                    if (bedrockBridge.isConnected.value) {
                        bedrockBridge.sendMinecraftCommand(target.command)
                    }

                    // Progress animation ticks
                    val steps = 5
                    for (s in 1..steps) {
                        delay(delayTime / steps)
                        val updated = _actionQueue.value.toMutableList()
                        if (firstPendingIndex < updated.size && updated[firstPendingIndex].status == ActionStatus.EXECUTING) {
                            updated[firstPendingIndex] = updated[firstPendingIndex].copy(progress = s.toFloat() / steps)
                            _actionQueue.value = updated
                        }
                    }

                    // Mark completed
                    val finishedList = _actionQueue.value.toMutableList()
                    if (firstPendingIndex < finishedList.size) {
                        finishedList[firstPendingIndex] = finishedList[firstPendingIndex].copy(
                            status = ActionStatus.COMPLETED,
                            progress = 1f
                        )
                        _actionQueue.value = finishedList
                    }
                }
            }
        }
    }

    fun toggleAutoQueue() {
        _isAutoQueueRunning.value = !_isAutoQueueRunning.value
    }

    fun setQueueSpeed(speedMs: Long) {
        _queueSpeedMs.value = speedMs
    }

    fun stepNextAction() {
        val current = _actionQueue.value.toMutableList()
        val index = current.indexOfFirst { it.status == ActionStatus.PENDING || it.status == ActionStatus.EXECUTING }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(status = ActionStatus.COMPLETED, progress = 1f)
            _actionQueue.value = current
            if (bedrockBridge.isConnected.value) {
                bedrockBridge.sendMinecraftCommand(item.command)
            }
            vibrateAlert()
        }
    }

    fun executeActionImmediately(action: TacticalAction) {
        val current = _actionQueue.value.toMutableList()
        val index = current.indexOfFirst { it.id == action.id }
        if (index != -1) {
            current[index] = action.copy(status = ActionStatus.COMPLETED, progress = 1f)
            _actionQueue.value = current
        }
        if (bedrockBridge.isConnected.value) {
            bedrockBridge.sendMinecraftCommand(action.command)
        }
        vibrateAlert()
    }

    fun dismissAction(actionId: String) {
        _actionQueue.value = _actionQueue.value.filterNot { it.id == actionId }
    }

    fun triggerEmergencyBunker() {
        vibrateAlert()
        val emergencyAction = TacticalAction(
            id = UUID.randomUUID().toString(),
            title = "EMERGENCY: 360° Cobblestone Bunker",
            description = "Seal yourself inside a 3-block high shelter immediately to reset mob targeting.",
            priority = ActionPriority.CRITICAL,
            status = ActionStatus.PENDING,
            command = "/setblock ~ ~-1 ~ cobblestone",
            durationMs = 400L,
            hotbarSlot = 1
        )
        val list = _actionQueue.value.toMutableList()
        list.add(0, emergencyAction)
        _actionQueue.value = list

        if (bedrockBridge.isConnected.value) {
            bedrockBridge.sendMinecraftCommand("title @a title §c[EMERGENCY BUNKER]")
        }
    }

    fun runVisionScan(bitmap: Bitmap?, customQuery: String, scenario: ScenarioPreset?) {
        viewModelScope.launch {
            _isScanning.value = true

            // If scenario preset chosen, apply scenario coordinates
            if (scenario != null) {
                _playerTelemetry.value = _playerTelemetry.value.copy(
                    biome = scenario.environment,
                    y = scenario.yLevel,
                    dimension = if (scenario.dimensionLabel.contains("Nether")) Dimension.NETHER else Dimension.OVERWORLD
                )
                _selectedDepthY.value = scenario.yLevel.toInt()
            }

            val result = geminiService.analyzeGameScreen(
                bitmap = bitmap,
                telemetry = _playerTelemetry.value,
                customPrompt = customQuery
            )
            _lastScanResult.value = result

            // Inject returned tactical actions into the top of the queue
            val updatedQueue = _actionQueue.value.toMutableList()
            updatedQueue.addAll(0, result.recommendedActions)
            _actionQueue.value = updatedQueue

            // Update blips if needed
            if (result.threatLevel == ThreatSeverity.FATAL) {
                vibrateAlert()
            }

            _isScanning.value = false
        }
    }

    fun sendGameMasterDialogue(userMessage: String) {
        val current = _gameMasterDialogue.value.toMutableList()
        current.add("Player" to userMessage)
        _gameMasterDialogue.value = current

        viewModelScope.launch {
            val response = geminiService.generateGameMasterQuest(
                tier = "Player Query: $userMessage",
                playerTelemetry = _playerTelemetry.value
            )
            val updated = _gameMasterDialogue.value.toMutableList()
            updated.add("Game Master" to response)
            _gameMasterDialogue.value = updated
        }
    }

    fun setSelectedDepth(y: Int) {
        _selectedDepthY.value = y
        _playerTelemetry.value = _playerTelemetry.value.copy(y = y.toDouble())
    }

    fun toggleBedrockBridge() {
        if (bedrockBridge.isServerRunning.value) {
            bedrockBridge.stop()
        } else {
            bedrockBridge.start(19132)
        }
    }

    fun sendBridgeCommand(cmd: String) {
        bedrockBridge.sendMinecraftCommand(cmd)
    }

    fun processNlpCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            _isNlpProcessing.value = true
            vibrateAlert()
            val result = nlpModule.translateCommand(command, forgeBridge.forgeState.value)
            _latestNlpResult.value = result
            val currentHistory = _nlpHistory.value.toMutableList()
            currentHistory.add(0, result)
            _nlpHistory.value = currentHistory

            // Translate ForgeActionInstruction into TacticalAction for the High-Speed Action Queue!
            val newTacticalActions = result.forgeActions.map { forgeAct ->
                val priority = when (forgeAct.actionType) {
                    ForgeActionType.ATTACK_ENTITY -> ActionPriority.COMBAT
                    ForgeActionType.MINE_BLOCK -> ActionPriority.RESOURCE
                    ForgeActionType.PLACE_BLOCK -> ActionPriority.SURVIVAL
                    ForgeActionType.MOVE_TO, ForgeActionType.LOOK_AT -> ActionPriority.NAVIGATION
                    else -> ActionPriority.SURVIVAL
                }
                TacticalAction(
                    id = forgeAct.id,
                    title = "[Forge] ${forgeAct.actionType.label}: ${forgeAct.description}",
                    description = "In-game Forge bot execution: ${forgeAct.inGameCommand}",
                    priority = priority,
                    status = ActionStatus.PENDING,
                    command = forgeAct.inGameCommand,
                    durationMs = forgeAct.delayMs,
                    hotbarSlot = forgeAct.slot?.plus(1)
                )
            }

            val updatedQueue = _actionQueue.value.toMutableList()
            updatedQueue.addAll(0, newTacticalActions)
            _actionQueue.value = updatedQueue

            // Auto execute the first action in Forge Bridge
            result.forgeActions.firstOrNull()?.let { firstAct ->
                forgeBridge.dispatchAction(firstAct)
            }

            _isNlpProcessing.value = false
        }
    }

    fun executeSingleForgeAction(action: ForgeActionInstruction) {
        viewModelScope.launch {
            forgeBridge.dispatchAction(action)
            vibrateAlert()
        }
    }

    fun executeAllForgeActions(actions: List<ForgeActionInstruction>) {
        viewModelScope.launch {
            for (action in actions) {
                forgeBridge.dispatchAction(action)
                delay(action.delayMs)
            }
            vibrateAlert()
        }
    }

    fun askStrategyAdvisor(query: String, category: StrategyCategory = StrategyCategory.ALL) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isAdvisorLoading.value = true
            vibrateAlert()
            val advice = strategyAdvisor.getAdvice(query, category)
            _advisorCurrentAdvice.value = advice
            val updated = _advisorHistory.value.toMutableList()
            updated.add(0, advice)
            _advisorHistory.value = updated
            _isAdvisorLoading.value = false
        }
    }

    fun selectHistoryAdvice(advice: StrategyAdvice) {
        _advisorCurrentAdvice.value = advice
    }

    fun pushAdviceToActionQueue(advice: StrategyAdvice) {
        if (advice.actionDirectives.isNotEmpty()) {
            val updatedQueue = _actionQueue.value.toMutableList()
            updatedQueue.addAll(0, advice.actionDirectives)
            _actionQueue.value = updatedQueue
            vibrateAlert()
            if (bedrockBridge.isConnected.value) {
                bedrockBridge.sendMinecraftCommand("title @a actionbar §a[Advisor] Loaded: ${advice.question.take(25)}")
            }
        }
    }

    fun setRadarMode(mode: RadarDisplayMode) {
        _radarMode.value = mode
        vibrateAlert()
    }

    fun toggleSquadBeacon(memberId: String) {
        _teamMembers.value = _teamMembers.value.map { member ->
            if (member.id == memberId) member.copy(isBeaconActive = !member.isBeaconActive) else member
        }
        vibrateAlert()
    }

    fun broadcastSquadPing(type: String, message: String) {
        viewModelScope.launch {
            vibrateAlert()
            val broadcastMsg = "[Squad Ping] $type: $message (Pos: X=${_playerTelemetry.value.x.toInt()}, Y=${_playerTelemetry.value.y.toInt()}, Z=${_playerTelemetry.value.z.toInt()})"
            if (bedrockBridge.isConnected.value) {
                bedrockBridge.sendMinecraftCommand("say §b$broadcastMsg")
                bedrockBridge.sendMinecraftCommand("title @a actionbar §b$broadcastMsg")
            }
            forgeBridge.dispatchAction(
                ForgeActionInstruction(
                    id = UUID.randomUUID().toString(),
                    actionType = ForgeActionType.EXECUTE_COMMAND,
                    description = "Broadcast Squad Ping: $type",
                    inGameCommand = "/tellraw @a [\"\",{\"text\":\"[MineMaster Squad] \",\"color\":\"aqua\",\"bold\":true},{\"text\":\"$type - $message\",\"color\":\"white\"}]"
                )
            )
        }
    }

    fun toggleAutoDispatch(enabled: Boolean) {
        _isAutoDispatchActive.value = enabled
    }

    fun autoDispatchStrategy(advice: StrategyAdvice) {
        viewModelScope.launch {
            if (advice.actionDirectives.isEmpty()) return@launch

            // Push to Action Queue
            val updatedQueue = _actionQueue.value.toMutableList()
            updatedQueue.addAll(0, advice.actionDirectives)
            _actionQueue.value = updatedQueue

            // Ensure queue execution is running
            if (!_isAutoQueueRunning.value) {
                _isAutoQueueRunning.value = true
                startQueueLoop()
            }

            vibrateAlert()

            val title = advice.question.take(24)
            if (bedrockBridge.isConnected.value) {
                bedrockBridge.sendMinecraftCommand("title @a actionbar §a[MineMaster Auto-Dispatch] Executing: $title")
            }

            // Immediately dispatch the initial action via Forge Bridge
            advice.actionDirectives.firstOrNull()?.let { firstAct ->
                val forgeAction = ForgeActionInstruction(
                    id = firstAct.id,
                    actionType = when (firstAct.priority) {
                        ActionPriority.COMBAT -> ForgeActionType.ATTACK_ENTITY
                        ActionPriority.RESOURCE -> ForgeActionType.MINE_BLOCK
                        else -> ForgeActionType.EXECUTE_COMMAND
                    },
                    description = firstAct.title,
                    inGameCommand = firstAct.command,
                    delayMs = firstAct.durationMs
                )
                forgeBridge.dispatchAction(forgeAction)
            }
        }
    }



    private fun vibrateAlert() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (ignored: Exception) {}
    }
}
