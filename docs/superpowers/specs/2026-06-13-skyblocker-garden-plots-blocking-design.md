# Skyblocker Garden Plots Widget RRV Blocking Integration

## Goal

Make Skyblocker’s `GardenPlotsWidget` register a `BlockingGuiComponent` with Reliable Recipe Viewer (RRV) so that RRV’s item list moves out of the way instead of rendering behind the widget.

## Background

- RRV computes overlay layout in `AbstractRrvOverlay.updateEffectiveDimensions()`.
- It reads `OverlayManager.INSTANCE.allGuiBlockings()` — a list of `BlockingGuiComponent` rectangles — and shrinks/shifts overlays away from any rectangle that intersects them.
- SkyRecipes already uses this mechanism in `CategoryButtonMixin` to reserve space for the category-button row above the search bar.
- `GardenPlotsWidget` (from Skyblocker) is a `104×132` widget added to `InventoryScreen` when the player is in the Garden and Skyblocker’s plot widget is enabled.

## Constraints

- Skyblocker must remain optional at runtime.
- SkyRecipes may add Skyblocker as a `compileOnly` dependency.
- Minimal mixins; use RRV plugin/internal API only when necessary.

## Chosen Approach

Direct mixin into `de.hysky.skyblocker.skyblock.garden.GardenPlotsWidget`, guarded by a Mixin config plugin so the mixin is only applied when Skyblocker is loaded.

### Files

| Action | Path |
|--------|------|
| Create | `src/main/java/com/github/kdgaming0/skyrecipes/mixin/SkyRecipesMixinPlugin.java` |
| Create | `src/main/java/com/github/kdgaming0/skyrecipes/mixin/skyblocker/GardenPlotsWidgetMixin.java` |
| Modify | `src/main/resources/skyrecipes.mixins.json` |
| Modify | `build.gradle.kts` |

### Mixin Lifecycle

1. **Constructor `@Inject(TAIL)`**
   - Register `BlockingGuiComponent` with the widget’s current bounds.
   - Attach a `ScreenEvents.remove` listener on the current screen to clean up on close.
   - Call `OverlayManager.INSTANCE.updateOverlaysAndWidgets(true)` so RRV immediately recomputes layout.

2. **`setX(int) @Inject(TAIL)`**
   - Re-register the blocking component with the new x coordinate.

3. **`setY(int) @Inject(TAIL)`**
   - Re-register the blocking component with the new y coordinate.

4. **Screen close**
   - `OverlayManager.INSTANCE.removeGuiBlocking(SKYRECIPES_GARDEN_PLOTS_ID, true)`.

### Blocking Component

```java
private static final Identifier SKYRECIPES_GARDEN_PLOTS_ID =
        Identifier.fromNamespaceAndPath("skyrecipes", "skyblocker_garden_plots");
```

Bounds are taken directly from the widget: `getX()`, `getY()`, `getWidth()`, `getHeight()`.

### Mixin Config Plugin

The plugin implements `IMixinConfigPlugin` and returns `false` from `shouldApplyMixin` for `GardenPlotsWidgetMixin` when `FabricLoader.isModLoaded("skyblocker")` is false. All other mixins remain unconditionally applied.

### Build Change

Add Skyblocker as a compile-only dependency:

```kotlin
modCompileOnly("maven.modrinth:skyblocker-liap:v6.4.1+26.1.2")
```

This matches the project’s current target Minecraft version (`26.1.2`).

## Rejected Alternatives

- **Reflection scan from `InventoryScreen`**: works without a compile dependency but is fragile and harder to keep in sync with Skyblocker updates.
- **RRV public API**: RRV exposes no public API for registering external blocking rectangles; `OverlayManager` is internal.

## Testing / Verification

- Build passes with and without Skyblocker present.
- With Skyblocker loaded, open the inventory in the Garden; the GardenPlotsWidget appears and RRV’s item list moves to the left of it.
- Toggle the recipe book; the widget repositions and RRV’s item list follows.
- Close the inventory; RRV returns to full width.

## Risks

- `OverlayManager` is an internal RRV API. If RRV refactors it, the mixin will need updating. This is consistent with existing SkyRecipes mixins that already depend on `OverlayManager`.
- Skyblocker could rename or remove `GardenPlotsWidget`. A compile dependency makes such a break visible at build time.
