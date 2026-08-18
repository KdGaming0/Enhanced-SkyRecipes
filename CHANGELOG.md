# 0.5.3

## Fixes
- Rift NPC names and NPCs whose NEU and SkyHanni names differ now resolve correctly when SkyHanni has a matching location.
- When SkyHanni does not know an NPC by name, SkyRecipes now offers a clickable coordinate fallback if you are on the correct island.
- Navigation now warns when the NPC is on a different island instead of directing you to meaningless coordinates on the current island.

# 0.5.2

## New
- The search bar calculator has been rebuilt with Smart expression detection, functions, calculation history, clearer errors, configurable number formatting, and an optional expanded-panel display.
- Calculator results can be copied with Enter or Ctrl+C, reused with Tab, or sent back to item search with Shift+Enter. Escape restores the item search that was visible before calculating.

## Compatibility
- Reliable Recipe Viewer's item list no longer overlaps Skyblocker's new Storage Overlay.

## Fixes
- The craft button now opens enchanted-book recipes correctly.

# 0.5.1

## Fixes
- Fixed a crash on startup with Reliable Recipe Viewer 8.7.0.

# 0.5.0

## Performance
- Better frame rate whenever a container is open with the item list showing.
- Searching for items in your inventory is more efficient.
- Scrolling and hovering the item list no longer costs a chunk of every frame, and grouped items (pets, minions, armor sets, sacks) no longer redo the same lookups every frame.
- Typing in the search bar responds faster, especially with stack groups enabled and on searches that match a lot of items.
- No more stutter when the item list is open while SkyRecipes is still loading or refreshing its data.
- SkyRecipes finishes loading quicker, and now spreads its loading work by time instead of a fixed amount per tick, so it never overruns a tick — no loading stutter on slower machines.
- Lower memory use and less garbage collection.

## Fixes
- Beginner sacks and Large Enchanted sacks now group together with the Small, Medium, and Large sacks instead of being displayed separately.
- Bronze and Silver Trophy Fishing and Trophy Frog sacks now group together too.

# 0.4.4

## Fixes
- Pressing the recipe or usage key on a shard now shows its fusions everywhere shards appear — the Hunting Box, the Attribute Menu, the Fusion Box, Shard Fusion and Confirm Fusion, and the Bazaar. Previously these menus showed unrelated player head recipes. Shards in those menus also highlight correctly when you search for them.

## Improvements
- Small optimizations to the item list.

# 0.4.3

## ✨ New

- Minecraft 26.2 support

## Fixes

- The craft button on a recipe now works for enchanted books, craftable pets, and attribute shards. Clicking it previously showed an "Invalid recipe" error for these items, it now opens the correct recipe.

# 0.4.2

## 🐛 Fixes

- Enchantment groups in the item list now show the enchantment's name — Sharpness, Bane of Arthropods, One For All — instead of every one of them being labelled "Enchanted Book".
- Pets, enchanted books, runes, and potions in the item list now show their price line from Skyblocker, like every other item already did.
- Fixed a crash when hovering pets, runes, or enchanted books in the item list while Skyblocker's NEU repository had failed to download.

## 🔧 Improvements

- SkyBlock items in the item list now carry the same item data the Hypixel server sends, so other SkyBlock mods read them correctly instead of treating them as unknown items.

# 0.4.1

## 🐛 Fixes

- Enchanted items now show their enchantment glint.
- Skyblocker's item backgrounds now appear behind items in the recipe viewer.

# 0.4.0

## ✨ New

### Shard Fusion recipes
- New **Shard Fusion** recipe category: every attribute shard now shows the fusion combinations that produce it, with input amounts and output quantity.
- Each fusion card has a **SkyShards** button (opens skyshards.com for optimal fusion paths and prices) and a **Bazaar** button.
- Clicking an attribute shard (Terra, Thorn, Draconic, and all the rest) now opens an info card instead of doing nothing. The card shows the shard's ability, rarity, category, shard ID, and family, plus a one-click buy on Bazaar button and wiki button.

### Tiered item grouping
- Tiered SkyBlock items are now grouped in the item list: each minion, pet, enchantment, drill, and accessory upgrade line collapses into a single expandable entry instead of listing every tier separately. Click a group to expand it; searching still shows exact tiers directly.
- Accessory upgrade lines (Talisman → Ring → Artifact → Relic) now group correctly — previously most of them were not recognized as upgrade chains.
- Armor upgrade lines group too: Kuudra armor (Crimson/Aurora/Terror/Fervor/Hollow → Hot → Burning → Fiery → Infernal) and crafted lines like Melon → Cropie → Squash → Fermento → Helianthus, Hardened Diamond → Mineral → Glossy Mineral, Snorkeling → Diver → Abyssal, and many more.
- Items that craft directly into one another now group as upgrade chains: compaction lines (Diamond → Enchanted Diamond → Enchanted Diamond Block), weapon lines (Aspect of the End → Aspect of the Void, Spider Sword → … → Sting), fishing rods, wands, and similar.
- Grouping is controlled by two new settings: **Group Tiered Items** and **Group Crafted Upgrade Chains**. Individual groups can be managed in RRV's Stack Groups screen.

### Other additions
- Skyblocker's item lookup keybinds now work on items in the Reliable Recipe Viewer item list. Hovering an item there and pressing the Item Price Lookup keybind shows the item on the Bazaar/Auction House — previously these keybinds did nothing in the item list.
- The recipe and usage keybinds (R/U by default) now work on items in Skyblocker's accessory bag helper, just like in the museum helper. Left-click still opens the wiki via Skyblocker.
- The Reliable Recipe Viewer item list now wraps around Skyblocker's accessory bag helper and its tab button instead of overlapping them.

## 🔧 Improvements

- Pressing `Tab` in the search bar now completes the suggested item name instead of jumping to the next field.
- Search filters can now target an exact requirement level with a second colon: `slayer:wolf:3` shows items requiring exactly Wolf Slayer 3, and `slayer:wolf:<=3`, `slayer:wolf:>3` etc. filter by level range. The same works for skills, e.g. `skill:combat:20`.
- The Reforge tab now only appears on items that can actually be reforged — accessories, hatcessories, carnival masks, and timecharms no longer show it.
- SkyBlock items no longer get pulled into Reliable Recipe Viewer's built-in vanilla groups.
- Performance: the item list loads faster, item search is more responsive, and there are smaller optimizations across the board.
- Garden mutation recipes that require water now show the HydroCan™ watering can as the water indicator instead of a small blue square.

### Craftables side panel
- The Craftables side panel now works with SkyBlock items: it lists everything you can craft or forge from the items currently in your inventory.
- By default an item only shows up when you have the full required amounts of every ingredient. A new **Craftables: Require Full Amounts** setting lets you switch to showing items as soon as you have at least one of each ingredient.

## 🐛 Fixes

- Fixed the wiki button sometimes not appearing when reopening a recipe by clicking an item in the item list. The button now shows reliably every time.
- Pressing the recipe/uses keybinds (`R` and `U`) on an enchanted book, pet, rune, or potion in your inventory now opens its recipe or info card correctly.
- Fixed some crafting recipes wrongly appearing in an item's "uses" list when the recipe outputs a different item than its ingredients suggest.
- Searching for an exact item ID ending in a digit (like `GIRAFFE;0` or `COBBLESTONE_GENERATOR_1`) now finds that item — previously these searches showed no results.
