package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun `diamond density peaks deep underground`() {
    val diamond = com.example.ui.components.MinecraftOres.first { it.name == "Diamond" }
    val densityAtMinus58 = diamond.getDensity(-58)
    val densityAtZero = diamond.getDensity(0)
    val densityAtSeaLevel = diamond.getDensity(64)

    assertTrue(densityAtMinus58 > densityAtZero)
    assertEquals(0f, densityAtSeaLevel)
  }

  @Test
  fun `ancient debris exists only in nether range`() {
    val debris = com.example.ui.components.MinecraftOres.first { it.name == "Ancient Debris" }
    val densityAt14 = debris.getDensity(14)
    val densityAtOverworld = debris.getDensity(64)
    assertTrue(densityAt14 > 0.8f)
    assertEquals(0f, densityAtOverworld)
  }

  @Test
  fun `nlp module translates stone mining variations into pickaxe select and mine actions`() {

    val nlp = com.example.data.nlp.MinecraftNlpModule()
    val state = com.example.data.forge.ForgeModBridge.createInitialSimulatedState()

    val variations = listOf("Mine some stone", "Quarry rocks", "Dig up cobblestone", "Harvest stone blocks")
    for (phrase in variations) {
      val result = nlp.parseCommandLocally(phrase, state)
      assertEquals("MINING", result.intent)
      assertTrue(result.feedbackMessage.isNotEmpty())
      assertTrue(result.forgeActions.any { it.actionType == com.example.data.model.ForgeActionType.SELECT_SLOT })
      assertTrue(result.forgeActions.any { it.actionType == com.example.data.model.ForgeActionType.MINE_BLOCK })
    }
  }

  @Test
  fun `nlp module translates shelter building into multi-step placement queue`() {
    val nlp = com.example.data.nlp.MinecraftNlpModule()
    val state = com.example.data.forge.ForgeModBridge.createInitialSimulatedState()

    val variations = listOf("Build a shelter", "Create a bunker", "Make a panic hut", "Throw up walls")
    for (phrase in variations) {
      val result = nlp.parseCommandLocally(phrase, state)
      assertEquals("CONSTRUCTION", result.intent)
      assertTrue(result.feedbackMessage.contains("shelter", ignoreCase = true) || result.feedbackMessage.contains("perimeter", ignoreCase = true))
      assertTrue(result.forgeActions.count { it.actionType == com.example.data.model.ForgeActionType.PLACE_BLOCK } >= 3)
    }
  }

  @Test
  fun `nlp module translates combat commands into sword select and attack actions`() {
    val nlp = com.example.data.nlp.MinecraftNlpModule()
    val state = com.example.data.forge.ForgeModBridge.createInitialSimulatedState()

    val result = nlp.parseCommandLocally("Kill that creeper approaching", state)
    assertEquals("COMBAT", result.intent)
    assertTrue(result.forgeActions.any { it.actionType == com.example.data.model.ForgeActionType.ATTACK_ENTITY })
  }

  @Test
  fun `strategy advisor provides actionable diamond sword crafting guide`() {
    val advisor = com.example.data.advisor.MinecraftStrategyAdvisor()
    val advice = advisor.getOfflineAdvice("How do I craft a diamond sword?", com.example.data.advisor.StrategyCategory.CRAFTING)

    assertEquals(com.example.data.advisor.StrategyCategory.CRAFTING, advice.category)
    assertTrue(advice.requiredMaterials.any { it.contains("Diamond") })
    assertTrue(advice.requiredMaterials.any { it.contains("Stick") })
    assertTrue(advice.steps.size >= 3)
    assertTrue(advice.proTips.isNotEmpty())
    assertTrue(advice.actionDirectives.isNotEmpty())
  }

  @Test
  fun `strategy advisor provides clear creeper defense tactics`() {
    val advisor = com.example.data.advisor.MinecraftStrategyAdvisor()
    val advice = advisor.getOfflineAdvice("What are the best ways to defend against creepers?", com.example.data.advisor.StrategyCategory.COMBAT_DEFENSE)

    assertEquals(com.example.data.advisor.StrategyCategory.COMBAT_DEFENSE, advice.category)
    assertTrue(advice.summary.contains("Shield", ignoreCase = true))
    assertTrue(advice.requiredMaterials.any { it.contains("Shield") })
    assertTrue(advice.steps.any { it.contains("Offhand") || it.contains("Shield") })
    assertTrue(advice.hazardWarnings.isNotEmpty())
  }

  @Test
  fun `strategy advisor provides automated farm schematic`() {
    val advisor = com.example.data.advisor.MinecraftStrategyAdvisor()
    val advice = advisor.getOfflineAdvice("Explain how to build a simple automated farm.", com.example.data.advisor.StrategyCategory.AUTOMATION_REDSTONE)

    assertEquals(com.example.data.advisor.StrategyCategory.AUTOMATION_REDSTONE, advice.category)
    assertTrue(advice.requiredMaterials.any { it.contains("Piston") })
    assertTrue(advice.requiredMaterials.any { it.contains("Observer") })
    assertTrue(advice.steps.size >= 5)
  }

  @Test
  fun `multiplayer radar correctly represents squad and rival threats`() {
    val squad = listOf(
      com.example.data.model.TeamMember(
        id = "t1",
        name = "Alex_Miner",
        role = "Scout",
        health = 20,
        maxHealth = 20,
        x = 138.5,
        y = -58.0,
        z = -875.0,
        distanceBlocks = 14f,
        angleDegrees = 45f
      )
    )

    val rival = com.example.data.model.MultiplayerThreat(
      id = "p1",
      playerName = "DreadLord_X",
      distanceBlocks = 24f,
      angleDegrees = 310f,
      armorTier = "Full Netherite",
      weapon = "Netherite Sword",
      severity = com.example.data.model.ThreatSeverity.FATAL
    )

    assertEquals(1, squad.size)
    assertEquals("Alex_Miner", squad[0].name)
    assertEquals(com.example.data.model.ThreatSeverity.FATAL, rival.severity)
    assertTrue(rival.distanceBlocks < 30f)
  }

  @Test
  fun `radar display modes toggle correctly`() {
    val modes = com.example.data.model.RadarDisplayMode.entries
    assertTrue(modes.contains(com.example.data.model.RadarDisplayMode.ALL_ENTITIES))
    assertTrue(modes.contains(com.example.data.model.RadarDisplayMode.SQUAD_AND_PVP))
    assertTrue(modes.contains(com.example.data.model.RadarDisplayMode.MOBS_ONLY))
  }
}




