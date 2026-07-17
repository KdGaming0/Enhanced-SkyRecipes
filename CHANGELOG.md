### 0.4.0

- Skyblocker's item lookup keybinds now work on items in the Reliable Recipe Viewer item list. Hovering an item there and pressing Item Price Lookup keybind now shows the item on the Bazaar/Auction House — previously these keybinds did nothing on items in the item list.
- Pressing Tab in the search bar now completes the suggested item name instead of jumping to the next field.
- Accessories, hatcessories, carnival masks and timecharms no longer show a Reforge tab — these can't be reforged, so the tab only appears for items now that correclt can be reforged.
- Tiered SkyBlock items are now grouped in the item list: each minion, pet, enchantment, drill, and accessory upgrade line collapses into a single expandable entry instead of listing every tier separately. Click a group to expand it; searching still shows exact tiers directly.
- SkyBlock items no longer get pulled into Reliable Recipe Viewer's built-in vanilla groups — previously every SkyBlock enchantment book was lumped into one giant "Enchanted Books" stack.
- Grouping can be turned off with the new "Group Tiered Items" setting, and individual groups can be managed in RRV's Stack Groups screen.
- Accessory upgrade lines (Talisman → Ring → Artifact → Relic) now group correctly — previously most of them were not recognized as upgrade chains.
- Armor upgrade lines now group too: Kuudra armor (Crimson/Aurora/Terror/Fervor/Hollow → Hot → Burning → Fiery → Infernal) and crafted upgrade lines like Melon → Cropie → Squash → Fermento → Helianthus, Hardened Diamond → Mineral → Glossy Mineral, Snorkeling → Diver → Abyssal, and many more.
- Items that craft directly into one another now group as upgrade chains: compaction lines (Diamond → Enchanted Diamond → Enchanted Diamond Block), weapon lines (Aspect of the End → Aspect of the Void, Spider Sword → … → Sting), fishing rods, wands, and similar. Controlled by the new "Group Crafted Upgrade Chains" setting (takes effect after the next data load).
- Attribute shards (Terra, Thorn, Draconic, and all the rest) now open an info card instead of doing nothing when clicked. The card shows the shard's ability, rarity, category, shard ID, and family, plus a Bazaar button that searches for the shard on the Bazaar in one click.
- New Shard Fusion recipe category: every attribute shard now shows the fusion combinations that produce it, with input amounts and output quantity.
  - Each fusion card has a SkyShards button (opens skyshards.com for optimal fusion paths and prices) and a Bazaar button.
- Fixed the wiki button sometimes not appearing when reopening a recipe by clicking an item in the item list. The button now shows reliably every time.