# Skyblocker Garden Plots Widget RRV Blocking Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline execution.

**Goal:** Register Skyblocker’s `GardenPlotsWidget` as an RRV `BlockingGuiComponent` so RRV’s item list moves out of the way instead of rendering behind the widget.

**Architecture:** A small Mixin config plugin conditionally applies a new mixin only when Skyblocker is loaded. The mixin injects into `GardenPlotsWidget` constructor, `setX`, and `setY` to keep a `BlockingGuiComponent` synchronized with the widget’s bounds. A screen-close listener removes the blocking component when the inventory closes.

**Tech Stack:** Java 25, Minecraft 26.1.2, Fabric Loader, Mixin, Fabric Screen Events API, RRV 8.3.0, Skyblocker 6.4.1+26.1.2 (compileOnly).

---

### Task 1: Add Skyblocker compile-only dependency

**Files:**
- Modify: `build.gradle.kts:81`

**Step 1: Add `modCompileOnly` line**

Insert after the RRV dependency block:

```kotlin
    modCompileOnly("maven.modrinth:skyblocker-liap:v6.4.1+26.1.2")
```

Expected final block:

```kotlin
    modCompileOnly("cc.cassian.rrv:reliable-recipe-viewer-fabric:${property("deps.rrv_version")}")
    modRuntimeOnly("cc.cassian.rrv:reliable-recipe-viewer-fabric:${property("deps.rrv_version")}")

    modCompileOnly("maven.modrinth:skyblocker:6.4.1+26.1.2")
```

---

### Task 2: Create the Mixin config plugin

**Files:**
- Create: `src/main/java/com/github/kdgaming0/skyrecipes/mixin/SkyRecipesMixinPlugin.java`

**Step 1: Write the plugin class**

```java
package com.github.kdgaming0.skyrecipes.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Conditionally applies mixins that target optional companion mods.
 */
public class SkyRecipesMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // no-op
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("GardenPlotsWidgetMixin")) {
            return FabricLoader.getInstance().isModLoaded("skyblocker");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // no-op
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // no-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // no-op
    }
}
```

---

### Task 3: Create the `GardenPlotsWidget` mixin

**Files:**
- Create: `src/main/java/com/github/kdgaming0/skyrecipes/mixin/skyblocker/GardenPlotsWidgetMixin.java`

**Step 1: Write the mixin**

```java
package com.github.kdgaming0.skyrecipes.mixin.skyblocker;

import cc.cassian.rrv.common.overlay.BlockingGuiComponent;
import cc.cassian.rrv.common.overlay.OverlayManager;
import de.hysky.skyblocker.skyblock.garden.GardenPlotsWidget;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers Skyblocker's GardenPlotsWidget as an RRV blocking GUI component
 * so the RRV item list moves out of the way.
 */
@Mixin(GardenPlotsWidget.class)
public class GardenPlotsWidgetMixin {

    @Unique
    private static final Identifier SKYRECIPES$GARDEN_PLOTS_ID =
            Identifier.fromNamespaceAndPath("skyrecipes", "skyblocker_garden_plots");

    @Inject(method = "<init>(II)V", at = @At("TAIL"))
    private void skyrecipes$onInit(int x, int y, CallbackInfo ci) {
        GardenPlotsWidget self = (GardenPlotsWidget) (Object) this;
        skyrecipes$updateBlocking(self);

        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            ScreenEvents.remove(screen).register(_ ->
                    OverlayManager.INSTANCE.removeGuiBlocking(SKYRECIPES$GARDEN_PLOTS_ID, true)
            );
        }
    }

    @Inject(method = "setX", at = @At("TAIL"))
    private void skyrecipes$onSetX(int x, CallbackInfo ci) {
        skyrecipes$updateBlocking((GardenPlotsWidget) (Object) this);
    }

    @Inject(method = "setY", at = @At("TAIL"))
    private void skyrecipes$onSetY(int y, CallbackInfo ci) {
        skyrecipes$updateBlocking((GardenPlotsWidget) (Object) this);
    }

    @Unique
    private void skyrecipes$updateBlocking(GardenPlotsWidget widget) {
        OverlayManager.INSTANCE.setGuiBlocking(new BlockingGuiComponent(
                SKYRECIPES$GARDEN_PLOTS_ID,
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight()
        ));
    }
}
```

---

### Task 4: Register the plugin and mixin

**Files:**
- Modify: `src/main/resources/skyrecipes.mixins.json`

**Step 1: Add the plugin and mixin entry**

Add the `plugin` key and the new mixin class:

```json
{
  "required": true,
  "package": "com.github.kdgaming0.skyrecipes.mixin",
  "compatibilityLevel": "JAVA_25",
  "plugin": "com.github.kdgaming0.skyrecipes.mixin.SkyRecipesMixinPlugin",
  "mixins": [
    "accessor.AbstractRrvItemListOverlayAccessor",
    ...
    "skyblocker.GardenPlotsWidgetMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

---

### Task 5: Build and verify

**Files:**
- Test via Gradle build

**Step 1: Sync and compile**

Run:

```bash
./gradlew :versions:26.1:compileJava
```

Expected: `BUILD SUCCESSFUL`.

**Step 2: Run a quick game test if a run config exists**

Run the client (optional, if setup allows):

```bash
./gradlew :versions:26.1:runClient
```

Verify in-game:
- Install Skyblocker alongside SkyRecipes.
- Open inventory in the Garden with the plot widget enabled.
- RRV item list renders to the left of the GardenPlotsWidget, not behind it.
- Toggle the recipe book; the item list follows the widget.

**Step 3: Update project docs**

Add a short entry to `IMPLEMENTATION_LOG.md` describing the new mixin and why it was added.

---

## Self-Review

- **Spec coverage:** build dep (Task 1), plugin (Task 2), mixin constructor/setX/setY + cleanup (Task 3), config registration (Task 4), build/test/docs (Task 5) — all covered.
- **Placeholder scan:** no TBD/TODO; exact version `6.4.1+26.1.2` used.
- **Type consistency:** `BlockingGuiComponent`, `OverlayManager`, `Identifier`, `GardenPlotsWidget`, and `ScreenEvents` usage match the spec and RRV 8.3.0 source.
