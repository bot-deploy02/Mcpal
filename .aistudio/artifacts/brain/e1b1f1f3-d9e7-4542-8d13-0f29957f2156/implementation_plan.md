# Multiplayer Server Threat & Team Radar with Strategy Auto-Dispatch

Empower MineMaster players on multiplayer survival servers (SMP) and faction realms with real-time squad telemetry, friendly team blips, hostile player alert vectors, and zero-latency auto-dispatching from the AI Strategy Advisor into active in-game bot execution.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed in Phase 1 interactive clarification:
> - **Primary Capability Focus**: Multiplayer server threat radar and squad/team tracking radar.
> - **Advisor & Queue Coordination**: Instant auto-dispatch mode—when a user selects or confirms strategy advice (e.g. defense, crafting, farming), steps are automatically converted into executable Forge/Bedrock directives and queued for sequential dispatch without requiring manual secondary confirmation.

- **Confirmed Decision 1**: Add a dedicated **Team & Server Radar Mode** to the 360° Tactical Radar, supporting squad member beacons (green/cyan blips, ping/waypoint sharing, health status) and rival/hostile player alerts (red blips, weapon detection, distance tracker).
- **Confirmed Decision 2**: Enable **Instant Auto-Dispatch** in the Strategy Advisor so players tapping any strategy guide immediately send the full operational sequence to their in-game bot queue.

---

## 1. Overview & Core Concept

- **What It Does**:
  - **Multiplayer Radar Mode**: Expands the tactical 360° radar to differentiate between hostile mobs, neutral entities, friendly team members (squad coordinates, HP, distance), and enemy multiplayer players (PvP threat rating, equipped gear, sprint velocity vector).
  - **Squad Waypoints & Ping Broadcast**: Allows sharing high-priority tactical pings (e.g., "Diamond Vein Spotted", "Lava Pit Hazard", "Enemy Inbound") across the squad.
  - **Auto-Dispatch Pipeline**: Streamlines the AI Strategy Advisor: queries generate structured, atomic steps that immediately enter the high-speed execution loop (`EXECUTE_COMMAND`, `SELECT_SLOT`, `PLACE_BLOCK`, `ATTACK_ENTITY`) and broadcast actionbar status directly to the player's HUD.
- **Target Audience**: Minecraft survival multiplayer (SMP), faction, and realm players who need tactical situational awareness and rapid automated execution while exploring or defending.
- **Key Value**: Replaces blind multiplayer wandering with 360° team situational awareness and transforms static advice into automated in-game actions.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Radar Mode Switching**:
   - On the Tactical Dashboard, a toggle allows switching between **Local Mob Radar** and **Multiplayer Squad & Threat Radar**.
   - Team members appear with custom player heads/initials, cyan distance rings, and health status indicators.
   - Rival players within 64 blocks appear with red pulsars, showing their active weapon (e.g. *Netherite Sword*) and approaching velocity vector.
2. **Squad Tactical Ping**:
   - Players can tap any coordinate or team member to broadcast a tactical beacon (e.g. *Rendezvous Point*, *Defend Position*, *Diamond Extraction*).
3. **One-Tap Strategy Auto-Dispatch**:
   - When viewing any guide in the Strategy Advisor (e.g., *Creeper Defense*, *Diamond Sword Crafting*, *Automated Farm*), an "AUTO-DISPATCH TO GAME" banner displays.
   - Tapping it instantly pushes all actions into the queue, activates the 350ms turbo or 1s normal dispatch timer, vibrates the device, and fires Bedrock/Forge bridge execution.

### Visual Identity & Theme
- **Obsidian & Neon Aesthetic**:
  - `ObsidianVoid` (`#0A0D10`) and `ObsidianSurface` (`#141920`) dark glassmorphism surfaces.
  - `DiamondCyan` (`#00F2FF`) for team beacons and friendly squad pings.
  - `RedstoneDanger` (`#FF3344`) for enemy PvP player alerts and weapon warnings.
  - `EmeraldGreen` (`#00E676`) for completed auto-dispatch transitions.
- **Tactical Radar Visuals**:
  - High-precision dual-color sweep canvas: green/cyan for friendlies, crimson pulse for hostile players.
  - Directional arrow indicators showing which way team members are moving.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified Team & PvP Player Telemetry Model**:
  - *Approach*: Extend the threat model to support `TeamMember` and `MultiplayerThreat` entities with health, dimension, distance, and equipped weapon.
  - *Why*: Allows SMP players to instantly assess PvP danger levels (e.g., enemy with enchanted diamond armor vs. unarmored player).
  - *Alternative Considered*: Separate screens for mobs vs. players was rejected to preserve split-second decision-making on a unified radar screen.
- **Decision 2: Direct In-Game Auto-Dispatch Pipeline**:
  - *Approach*: When advice is loaded with auto-dispatch enabled, actions automatically stream through the Forge Mod Bridge and Bedrock WebSocket Bridge with animated progress.
  - *Why*: Directly fulfills user preference for streamlined zero-friction automation during gameplay.
  - *Alternative Considered*: Manual multi-step modal prompts would slow the player down during live combat and mining.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                       MineMaster App                        │
│                                                             │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │   Strategy Advisor    │       │  Multiplayer Radar    │  │
│  │  (Gemini + Knowledge) │       │ (Team & Enemy Player) │  │
│  └───────────┬───────────┘       └───────────┬───────────┘  │
│              │ Auto-Dispatch                 │ Live Sweep   │
│              ▼                               ▼              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                   CopilotViewModel                    │  │
│  │  - actionQueue (StateFlow<List<TacticalAction>>)      │  │
│  │  - teamMembers (StateFlow<List<TeamMember>>)          │  │
│  │  - pvpThreats (StateFlow<List<MultiplayerThreat>>)    │  │
│  └───────────┬───────────────────────────────┬───────────┘  │
│              │                               │              │
│              ▼                               ▼              │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │   Forge Mod Bridge    │       │ Bedrock WS Bridge     │  │
│  │ (HTTP / RPC Executor) │       │ (Port 19132 /connect) │  │
│  └───────────────────────┘       └───────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### State & Model Additions
1. `TeamMember`:
   - `id: String`, `name: String`, `x: Double`, `y: Double`, `z: Double`, `health: Int`, `distance: Float`, `angle: Float`, `status: String`
2. `MultiplayerThreat`:
   - `id: String`, `playerName: String`, `distance: Float`, `angle: Float`, `armorTier: String`, `weapon: String`, `threatLevel: ThreatSeverity`
3. `CopilotViewModel`:
   - `teamMembers: StateFlow<List<TeamMember>>`
   - `pvpThreats: StateFlow<List<MultiplayerThreat>>`
   - `radarMode: StateFlow<RadarDisplayMode>` (`ALLIES_AND_THREATS`, `MOBS_ONLY`)
   - `autoDispatchAdvice(advice: StrategyAdvice)`
4. UI Enhancements:
   - `TacticalRadarView`: Dual-mode canvas rendering squad beacons and enemy PvP radar blips.
   - `StrategyAdvisorScreen`: Auto-dispatch visual trigger with live status indicators.
