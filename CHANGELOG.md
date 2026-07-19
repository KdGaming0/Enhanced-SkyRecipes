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
