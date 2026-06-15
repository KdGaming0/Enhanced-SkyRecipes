<div align="center">

[![Download on Modrinth](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/available/modrinth_vector.svg "Download SkyRecipes on Modrinth")](https://modrinth.com/mod/skyrecipes)
[![Join Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg "Join the SkyRecipes Discord server")](https://discord.gg/FCPP2WPZ3U)
[![Requires Fabric API](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg "Requires Fabric API")](https://modrinth.com/mod/fabric-api)

</div>
<div align="center">

[![Requires RRV](https://img.shields.io/badge/requires-Reliable%20Recipe%20Viewer-9B59B6?logo=minecraft "Requires Reliable Recipe Viewer")](https://modrinth.com/mod/rrv)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/skyrecipes?color=00AF5C&label=downloads&logo=modrinth "Total SkyRecipes downloads on Modrinth")](https://modrinth.com/mod/skyrecipes)
[![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=for-the-badge "Join the Fluxer community")](https://fluxer.gg/3jJy9cp6)

# SkyRecipes

**The complete SkyBlock recipe viewer — 8,000+ items, 11 recipe types, and powerful search.**

Browse crafting recipes, forge recipes, mob drops, NPC shops, essence upgrades, reforges, garden mutations, and more. Built on Reliable Recipe Viewer as its display layer, with data sourced from the NEU repository and kept up to date automatically.
</div>

## What is SkyRecipes?

SkyRecipes is a standalone Fabric client mod that adds the full SkyBlock item and recipe database to Minecraft, displayed through Reliable Recipe Viewer (RRV). On first launch it downloads and compiles data from the [NotEnoughUpdates Repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO), then keeps it cached and refreshed in the background. The result: instant recipe lookup, powerful search, and a clean UI for every SkyBlock item.

## Recipe Types

SkyRecipes registers **11 custom SkyBlock recipe categories** in RRV. Press `R` over an item to see how it's crafted or obtained, and `U` to see what recipes use it.

**SkyBlock Crafting** — Classic 3×3 crafting grid recipes pulled from the NEU `recipe` field. Shows ingredients, output count, craft requirements, and a direct link to the SkyBlock wiki.

**SkyBlock Forge** — Forge recipes with up to 9 ingredient slots, output item, and craft duration (e.g. *"2h 30m"*). Includes forge unlock requirement hints.

**SkyBlock Mob Drops** — Drop tables showing where you can obtain an item. Each entry displays its drop chance (e.g. *25% drop chance*).

**SkyBlock NPC Shop** — Shop recipes showing the cost of an item and what you receive in return.

**SkyBlock NPC Info** — Info cards for every NPC (`*_NPC` items) showing their island, coordinates, and a **Navigate** button to locate them in-game. Requires [SkyHanni](https://modrinth.com/mod/skyhanni).

**SkyBlock Kat Upgrade** — Kat pet upgrade recipes showing the input pet, required materials, coin cost, upgrade time, and resulting pet tier.

**SkyBlock Trade** — Simple trade recipes — one cost item, one result item.

**SkyBlock Wiki Info** — Fallback info card for items that have wiki URLs but no other recipe data, keeping every SkyBlock item accessible inside the viewer.

**SkyBlock Essence Upgrade** — Essence upgrade recipes showing the input item, essence type and cost per star, any companion materials required, and the upgraded output.

**SkyBlock Reforge** — Reforge recipes showing what stats each reforge provides. Displays the input item, reforge stone, applicable rarities, and a blacksmith NPC preview.

**SkyBlock Garden Mutation** — A built-in garden mutation reference with a 6×6 grid layout, surface/water requirements, spreading conditions, effects, and copper cost. Quickly look up the required layout for any mutation.

## Search & Discovery

### Smart Autocomplete
Type in the RRV search bar and SkyRecipes shows gray **ghost-text suggestions** matching item display names, internal names, aliases, and recipe page names. Press `Right Arrow` or `Tab` to accept a suggestion instantly.

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
Click the **category toggle buttons** above the search bar to filter the item list to Armor, Weapons, Tools, Accessories, Pets, Enchanted Books, Minions, Equipment, or Materials.

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
| **Boolean flags** | `dungeon`, `rift`, `soulbound`, `bazaar`, `craftable`, `forgeable`, `npc`, `vanilla`, `pet`, `accessory` | Filter by property |
| **Exact phrase** | `"mining speed"` | The words must appear **together on one line** of the name or lore |
| **Regex** | `/mining.*fortune/` | Full regular expression, case-insensitive, matched one line at a time |

Combine filters freely: `rarity:legendary damage>200 dungeon sword` finds legendary dungeon swords with more than 200 damage.

#### Exact phrases & regex

Plain keywords are matched independently and can land **anywhere** in an item's name or lore — even on different lines. So `reforge stone mining speed` also turns up items that merely happen to mention "mining" and "speed" on separate lines (the Sunstone, for example), not just true mining-speed reforges.

Wrap words in **double quotes** to require them as a contiguous phrase on a single line:
- `reforge stone "mining speed"` — reforge stones whose effect text actually reads "mining speed"
- `"reforge stone" "mining speed"` — same result, with both parts required as phrases

For full power, put a **regular expression** between slashes. It's case-insensitive and matched one line at a time (like `grep`), so it never bridges two lore lines:
- `/mining (speed|fortune)/` — lines mentioning mining speed **or** mining fortune
- `/\+\d+ mining speed/` — a numeric mining-speed bonus such as "+30 Mining Speed"

> Type the alternation pipe `|` **directly** — `/(speed|fortune)/`. Don't write `\|`; in a regex that means a literal `|` character and the alternation won't work.

**New to regex?** A regular expression is a tiny pattern language for describing text. You only need a handful of pieces:

- `.` — any single character; `.*` — any run of characters ("anything in between")
- `\d` — any digit `0–9`; `\d+` — one or more digits, i.e. a number like `30`
- `+` — one or more of the item before it; `*` — zero or more; `?` — makes the item before it optional (`swords?` matches "sword" and "swords")
- `a|b` — matches `a` **or** `b`; wrap the choices in parentheses to group them: `(speed|fortune)`
- `[abc]` — any one of the listed characters (`[ivx]` matches a roman-numeral letter)
- `^` — the start of the line; `$` — the end of the line (`^aspect` matches lines that *begin* with "aspect")
- `\` — makes the next special character literal, so `\+` matches a real `+` and `\.` a real `.`

Plain letters and spaces just match themselves, so you can mix them in. Putting it together, `/\+\d+ (speed|fortune)/` reads as: a literal `+`, then a number, a space, then "speed" or "fortune" — matching stat lines like "+30 Speed" or "+12 Fortune". Everything is case-insensitive and checked one line at a time, so you never have to worry about upper/lowercase or a pattern spilling across two lore lines.

Phrases and regex combine with every other filter — e.g. `%WEAPON rarity:legendary "ability damage"`. A half-typed or invalid regex is simply ignored until it's complete, so your results never blank out while you type. Plain keyword searches are completely unaffected.

### Search Calculator
Evaluate math directly in the RRV search bar. The result appears as gray ghost text next to your query — no need to press anything.

**Basic arithmetic**
- `10+10` → **20**
- `50m / 1.2k` → **41,666.66667**
- `(1000+500)*2` → **3,000**

**SkyBlock unit suffixes** (no spaces needed)
- `k` → ×1,000 (e.g. `1.5m + 250k` → **1,750,000**)
- `m` → ×1,000,000
- `b` → ×1,000,000,000
- `t` → ×1,000,000,000,000
- `s` → ×64 (stack, e.g. `27s` → **1,728**)
- `e` → ×160 (enchanted item, e.g. `10e` → **1,600**)
- `%` → ÷100, a postfix percentage modifier (e.g. `50 * 10%` → **5**)

**Operators**
- `+` `-` `*` `/` `^` (power)
- `x` can be used in place of `*` for multiplication
- `**` is treated as `^`

**Config:** `calculatorPrecision` controls decimal places (0–10, default 5).

### Family Expansion
Tiered items — dungeon stars, pet tiers, minion tiers, accessory upgrades, and enchantment levels — are grouped into families. When family expansion is enabled, pressing `R` on one member can show recipes across the entire family, so you don't have to hunt down each tier separately.

## Configuration

Open the config through **Mod Menu → SkyRecipes → Config**. SkyRecipes uses MidnightLib, so all options are editable in-game with live saving.

| Category | Option | Description |
|----------|--------|-------------|
| **UI** | `calculatorEnabled` | Enables math expressions in the RRV search bar |
| **UI** | `calculatorPrecision` | Decimal places for search-bar calculator results (0–10) |
| **UI** | `familyExpansionEnabled` | Groups tiered items (pets, minions, stars, accessory upgrades) into families |
| **RRV** | `hideCategoryButtons` | Completely hides the category icon row above the RRV search bar |
| **RRV** | `hideCategoryButtonsWhenNotSearching` | Hides category buttons when RRV is in "Only visible when searching" mode and the search bar is empty |
| **RRV** | `hideEmptyBookmarkPanel` | Auto-hides the bookmark side panel when no bookmarks exist |
| **RRV** | `wideRrvSearchBar` | Expands the RRV search bar (centred mode: up to the configured width; item-list mode: limited by available space) |
| **RRV** | `rrvSearchBarWidth` | Minimum width for the search bar when wide mode is enabled |
| **RRV** | `rrvItemListWidthPercent` | Adjusts the RRV item-list overlay width (25–100%) |
| **RRV** | `rrvSidePanelWidthPercent` | Adjusts the RRV side-panel overlay width (25–100%) |

Recipe category visibility is managed natively by RRV through its **Recipe Category Config** screen.

## Installation

1. Install **Minecraft** with the **Fabric Loader** for 1.21.1.
2. Download the latest `.jar` from [Modrinth](https://modrinth.com/mod/skyrecipes).
3. Place **Fabric API** in your `mods` folder. MidnightLib is bundled inside the SkyRecipes jar, so no separate download is needed.
4. Install **[Reliable Recipe Viewer (RRV)](https://modrinth.com/mod/rrv)** to see the recipes in-game.
5. Launch the game. On first launch, SkyRecipes will download and compile the NEU repository in the background.
6. Once the data is ready, press your RRV keybind (default `R` / `U`) on any SkyBlock item.

[<img height="40" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg" alt="Download SkyRecipes on Modrinth">](https://modrinth.com/mod/skyrecipes)

## Support & Community

Have a bug report, feature request, or just want to hang out?

[![Join Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg "Join the SkyRecipes Discord server")](https://discord.gg/FCPP2WPZ3U)
[![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=for-the-badge "Join the Fluxer community")](https://fluxer.gg/3jJy9cp6)

## Support the Project

Want to support development? You can do so on **Ko-fi**. All donations are greatly appreciated and help keep the mod updated.

[**Support on Ko-fi**](https://ko-fi.com/kdgaming1)

<div align="center">

**Made for the SkyBlock community.**

</div>