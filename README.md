# Arcane Enchanting

An enhanced enchanting table for **Minecraft 1.7.10 (Forge 10.13.4.1614)**. The Arcane Enchanting Table keeps
the vanilla enchanting rules and bookshelf setup, and adds a stronger visual presence, an enchanting ritual, and
more predictable enchanting choices.

## 1. Features

### 1.1 Visual states

| State | Trigger | What is shown |
|---|---|---|
| Dormant | No player within 7 blocks | Tiered pedestal, four floating corner crystals, closed book, faint rune inlays |
| Awakened | A player approaches | Rune inlays brighten; the book rises and opens; a magic circle rotates on the ground; rune rings orbit the table; light threads connect every contributing bookshelf to the book; glyph particles flow along them; motes rise from the circle |
| Ritual (charge) | An option is chosen | The item rises above the table and spins faster; rings contract around it; crystals link to it with light; energy converges from all directions; rising chimes |
| Ritual (climax) | End of the charge | Enchantments are applied (the glint appears at this moment); flash, light pillar, double ground shockwave, particle burst |
| Ritual (completion) | About 1.2 s later | The enchanted item flies into the player's inventory |

**Power visualisation.** The number of rune rings reflects the bookshelf count: the first ring is always present
when awakened (dim below 5 shelves), the second fades in between 5 and 10 shelves, and the third between 10 and 15
shelves (15 shelves = full power). During a ritual all three rings appear.

### 1.2 Gameplay changes

| Change | Description |
|---|---|
| Revealed enchantment | Hovering an option shows one guaranteed enchantment and the number of hidden ones |
| Stable options | Options are derived from a per-player seed, so the same item shows the same options until you enchant or re-scry. Taking the item out and putting it back no longer rerolls for free |
| Re-scry | A button that rerolls the three options for a configurable cost (default: 1 level) |
| Ritual | Enchanting closes the screen and plays the ritual; the item is returned automatically |

Unchanged from vanilla 1.7.10: bookshelf placement rules (modded bookshelves are supported through Forge), the
enchantment level calculation, the full-level experience cost, and the rule that books lose one rolled enchantment.

### 1.3 Recipe

```
 L D L      L = Lapis Lazuli Block
 O T O      D = Diamond
 O O O      O = Obsidian
            T = Enchanting Table
```

## 2. Building

Requirements: a JDK 17 or newer to run Gradle (the build provisions its own toolchains), and network access to
the hosts below. In a restricted network (for example a sandbox with an allow-list), every host must be reachable.

| Host | Used for |
|---|---|
| `services.gradle.org` | Gradle distribution |
| `plugins.gradle.org`, `repo.maven.apache.org` | Gradle plugins and Maven Central libraries |
| `api.foojay.io` | Java toolchain download (not needed when the JDKs are installed locally) |
| `nexus.gtnewhorizons.com` | GTNH build plugins and libraries |
| `maven.minecraftforge.net`, `files.minecraftforge.net` | Forge, MCP mappings, the Fernflower decompiler |
| `piston-meta.mojang.com`, `launchermeta.mojang.com` | Minecraft version metadata |
| `piston-data.mojang.com`, `launcher.mojang.com` | Minecraft client and server jars |
| `libraries.minecraft.net`, `resources.download.minecraft.net` | Minecraft libraries and assets |
| `files.prismlauncher.org` | Patched log4j used by Minecraft 1.7.10 |
| `repo.spongepowered.org` | Legacy Forge libraries (`lzma`) |
| `raw.githubusercontent.com` | MCP mapping files |

Maven Central can answer with HTTP 429 (rate limiting), which stops dependency resolution. If this happens, run
the build again, or place the GTNH Nexus (`https://nexus.gtnewhorizons.com/repository/public/`, which mirrors
Maven Central) first in the repository list with a local Gradle init script.

```bash
./gradlew build          # produces build/libs/arcaneenchanting-<version>.jar
./gradlew runClient      # starts a development client with the mod loaded
```

Copy the jar from `build/libs/` (the file without `-dev` or `-sources` in its name) into the `mods` folder.

The build uses the [GTNH 1.7.10 mod template](https://github.com/GTNewHorizons/ExampleMod1.7.10)
(RetroFuturaGradle). Spotless formatting checks are disabled in `gradle.properties`.

## 3. Configuration

File: `config/arcaneenchanting.cfg` (created on first launch).

| Category | Key | Default | Meaning |
|---|---|---|---|
| gameplay | `rescryCost` | 1 | Levels consumed by Re-scry (the server value is shown to clients) |
| gameplay | `ritualTicks` | 60 | Base ritual length in ticks |
| gameplay | `ritualTicksPerTier` | 12 | Extra ticks for the second and third option |
| visual | `particleMultiplier` | 1.0 | Particle amount (0 disables custom particles); also scaled by the game's particle setting |
| visual | `additiveGlow` | true | Additive blending for glow effects |
| visual | `groundCircle` | true | Ground magic circle |
| visual | `shelfThreads` | true | Bookshelf light threads |
| visual | `glowRenderPass` | 1 | Render pass for translucent effects |
| audio | `ambientHum` | true | Quiet hum near an awakened table |
| audio | `soundVolume` | 1.0 | Volume multiplier |

### 3.1 Shader packs (Angelica, Complementary Reimagined)

The glow effects are drawn with immediate-mode OpenGL in the tile entity renderer. Shader pipelines may process
these draws differently from the vanilla pipeline. If something looks wrong with a shader pack enabled:

1. Glow shapes appear dark, opaque or boxy: set `additiveGlow=false`.
2. Glow effects are missing entirely: set `glowRenderPass=0`.

## 4. Project layout

```
src/main/java/com/arcaneenchanting/
  ArcaneEnchanting.java, CommonProxy.java, Config.java
  block/BlockArcaneTable.java          block, icons, GUI opening
  tile/TileArcaneTable.java            ritual state machine, persistence, sync, block events
  tile/TableAnimationState.java        client animation values
  inventory/ContainerArcaneTable.java  server-authoritative container, data sync, actions
  inventory/EnchantOptions.java        seeded option computation, per-player seed
  inventory/GuiHandler.java
  util/ArcaneGeometry.java             bookshelf scan (shared)
  client/ClientProxy.java
  client/RenderArcaneTable.java        tile entity renderer (solid and glow passes)
  client/ItemRendererArcaneTable.java  item rendering
  client/GuiArcaneTable.java           enchanting screen
  client/TableClientTicker.java        animation, particles, sounds
  client/TableLayout.java              shared geometry constants
  client/fx/EntityArcaneMoteFX.java    glowing particle
src/main/resources/assets/arcaneenchanting/
  lang/en_US.lang, lang/zh_CN.lang
  textures/blocks/*.png                16x16 block-atlas icons
  textures/fx/*.png                    effect textures (+ .mcmeta for filtering)
tools/gen_textures.py                  regenerates every texture (Python 3 + Pillow)
```

All textures are original and generated by `tools/gen_textures.py`. Edit the script and run
`python3 tools/gen_textures.py` to change them.

## 5. Test checklist

1. Craft the table with the recipe above; check the item in the inventory, in the hand and dropped on the ground.
2. Place it with 0, 6 and 15 bookshelves; walk towards it and away from it (ring count, threads, awakening sound).
3. Place a sword: verify three options, the revealed enchantment in the tooltip, and that removing and re-inserting
   the sword shows the same options.
4. Use Re-scry with and without enough levels.
5. Choose each option and watch the ritual; verify the enchantments match the tooltip and the item arrives in the
   inventory.
6. Enchant a book; verify it becomes an enchanted book.
7. Break the table during a ritual; the item must drop, enchanted.
8. Save and quit during a ritual, then reload; the ritual must resume and finish.
9. Repeat steps 2 and 5 with the shader pack enabled; adjust `additiveGlow` / `glowRenderPass` if needed.
