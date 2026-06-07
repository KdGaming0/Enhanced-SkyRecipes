<div align="center">

[![Download on Modrinth](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/mod/skyrecipes)
[![Join Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/FCPP2WPZ3U)
[![Requires Fabric API](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg)](https://modrinth.com/mod/fabric-api)
[![Requires RRV](https://img.shields.io/badge/requires-Reliable%20Recipe%20Viewer-9B59B6?logo=minecraft)](https://modrinth.com/mod/rrv)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/skyrecipes?color=00AF5C&label=downloads&logo=modrinth)](https://modrinth.com/mod/skyrecipes)
[![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=for-the-badge)](https://fluxer.gg/3jJy9cp6)

**The ultimate SkyBlock recipe viewer for Reliable Recipe Viewer (RRV).**
Browse 8,000+ Hypixel SkyBlock items, recipes, drops, NPC shops, essence upgrades, reforges, garden mutations, and more — all sourced from the NEU repository and kept up to date automatically.

</div>

---

## What is SkyRecipes?

SkyRecipes is a standalone Fabric client mod that brings the full SkyBlock item and recipe database into [Reliable Recipe Viewer (RRV)](https://modrinth.com/mod/rrv). It downloads and compiles data from the [NotEnoughUpdates Repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) on first launch, then keeps it cached and refreshed in the background. The result: instant recipe lookup, powerful search, and a clean UI for every SkyBlock item.

---

## Recipe Types

SkyRecipes registers **11 custom SkyBlock recipe categories** in RRV. Press `R` over an item to see how it's crafted or obtained, and `U` to see what recipes use it.

**SkyBlock Crafting** — Classic 3×3 crafting grid recipes pulled from the NEU `recipe` field. Shows ingredients, output count, craft text requirements, and a direct link to the SkyBlock wiki.

**SkyBlock Forge** — Forge recipes with up to 9 ingredient slots, output item, and a craft duration (e.g. *"2h 30m"*). Includes forge unlock requirement hints.

**SkyBlock Mob Drops** — Mob drop table see where you can get an item. Each drop shows its chance for it to drop (e.g. *"50%"*, *"1/250"*, *"RNGesus Drop"*). Mobs are previewed with their NEU skin or vanilla entity render.

**SkyBlock NPC Shop** — NPC shop recipes display the cost for an item and the item you get in return. Includes a rotating NPC entity preview and an **NPC Info** button that opens the merchant's info card.

**SkyBlock NPC Info** — Info cards for every NPC (`*_NPC` items) showing their head, island, coordinates, and a **"Navigate"** to easly navigate to the npc, requiers [SkyHanni](https://modrinth.com/mod/skyhanni).

**SkyBlock Kat Upgrade** — Kat pet upgrade recipes showing the input pet, required materials, coin cost, upgrade time, and the resulting pet tier.

**SkyBlock Trade** — Simple trade recipes — one cost item, one result item — with support for optional `min`/`max` range display.

**SkyBlock Wiki Info** — Fallback info card for items that have wiki URLs but no other recipe data. Keeps every SkyBlock item clickable inside the viewer.

**SkyBlock Essence Upgrade** — Essence upgrade recipes. Shows the input item, essence type and cost per star, any companion materials need, and the upgraded output.

**SkyBlock Reforge** — Reforge recipes see what stats each reforge gives. Displays the input item, reforge stone, applicable rarities, and a blacksmith NPC preview.

**SkyBlock Garden Mutation** — Built-in garden mutation reference with a 6×6 grid layout, surface/water requirements, spreading conditions, effects, and copper cost. Easily easy the required layout for any mutation.

---

## Search & Discovery

### Smart Autocomplete
Type in the RRV search bar and SkyRecipes shows gray **ghost-text suggestions** for item display names, internal names, aliases, and recipe page names. Press `Right Arrow` or `Tab` to accept a suggestion instantly.

### Common Aliases
SkyRecipes understands SkyBlock shorthand. Try searching:
- `aote` → Aspect of the End
- `aotv` → Aspect of the Void
- `juju` → Juju Shortbow
- `term` → Terminator
- `hype` → Hyperion
- `gdrag` → Golden Dragon Pet
- `valk` → Valkyrie
- ...and more.

### Category Filtering
Click the **category toggle buttons** above the search bar to filter the item list to only Armor, Weapons, Tools, Accessories, Pets, Enchanted Books, Minions, Equipment, or Materials.

You can also search by category path:
- `%ARMOR` — all armor
- `%ARMOR/HELMET` — helmets only
- `%PET` — pets
- `%TOOL` — tools
- `%WEAPON` — weapons

### Advanced Search Syntax
SkyRecipes supports structured queries beyond plain keywords:

| Syntax | Example | Meaning |
|--------|---------|---------|
| **Stat thresholds** | `damage>100` | Items with more than 100 damage |
| | `health<=500` | Items with 500 or less health |
| **Rarity filter** | `rarity:legendary` or `r:l` | Legendary items only |
| **Type filter** | `type:sword` or `t:bow` | Swords or bows |
| **Slayer req** | `slayer:zombie>3` | Requires Zombie Slayer III+ |
| **Skill req** | `skill:combat>20` | Requires Combat 20+ |
| **Catacombs req** | `cata>=5` | Requires Catacombs 5+ |
| **Boolean flags** | `dungeon`, `rift`, `soulbound`, `bazaar`, `craftable`, `forgeable`, `npc` | Filter by property |

Combine them freely: `rarity:legendary damage>200 dungeon sword` finds legendary dungeon swords with more than 200 damage.

### Search Calculator
Evaluate math directly in the RRV search bar. Great for quick bulk calculations:

- `1.5m + 250k` → **1,750,000**
- `64 * 9 * 160` → **92,160**
- `50m / 1.2k` → **41,666.67**

Supports `k`/`m`/`b` suffixes, parentheses, exponentiation (`^`), modulo (`%`), and decimal values.

### Family Expansion
Tiered items (dungeon stars, pet tiers, minion tiers, accessory upgrades, enchantment levels) are grouped into families. When family expansion is enabled, pressing `R` on one member can show recipes across the whole family, so you don't have to hunt down each tier separately.

---

## Configuration

Open the config through **Mod Menu** → **SkyRecipes** → **Config**. SkyRecipes uses MidnightLib, so all options are editable in-game with live saving.

| Category | Option | Description |
|----------|--------|-------------|
| **UI** | `searchCategoryButtonsVisible` | Show the compact category icon row above the search bar |
| **UI** | `familyExpansionEnabled` | Group related tiered items (pets, minions, stars, accessory upgrades) into families |

Recipe category visibility is managed natively by RRV through its **Recipe Category Config** screen.

---

## Installation

1. Install **Minecraft** with the **Fabric Loader** for 1.26.1.
2. Download the latest `.jar` from [Modrinth](https://modrinth.com/mod/skyrecipes).
3. Put **Fabric API** in your `mods` folder. MidnightLib is bundled inside the SkyRecipes jar, so you don't need to download it separately.
4. Install **[Reliable Recipe Viewer (RRV)](https://modrinth.com/mod/rrv)** to see the recipes in-game.
5. Launch the game. On first launch, SkyRecipes will download and compile the NEU repository in the background.
6. Once the data is ready, press your RRV keybind (default `R` / `U`) on any SkyBlock item.

[<img height="40" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg">](https://modrinth.com/mod/skyrecipes)

---

## Support & Community

Have a bug report, feature request, or just want to hang out?

[![Join Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/FCPP2WPZ3U)
[![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=for-the-badge)](https://fluxer.gg/3jJy9cp6)

---

## Support the Project

Want to support development? You can do that on **Ko-fi**. All donations are highly appreciated and help keep the mod updated.

[**Support on Ko-fi**](https://ko-fi.com/kdgaming1)

---

<div align="center">

**Made with love for the SkyBlock community.**

</div>
