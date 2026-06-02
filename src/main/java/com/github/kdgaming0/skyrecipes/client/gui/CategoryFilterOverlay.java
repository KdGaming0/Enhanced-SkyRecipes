package com.github.kdgaming0.skyrecipes.client.gui;

import com.github.kdgaming0.skyrecipes.client.config.SkyRecipesConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a category-filter button row when the RRV item overlay is visible.
 *
 * <p>Buttons reflect the current config state (green = enabled, gray = disabled).
 * Clicking a button toggles its config value. Changes take effect on the next
 * RRV reload (e.g. F3+T or world change).</p>
 */
public final class CategoryFilterOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryFilterOverlay.class);
    private static final int BUTTON_W = 28;
    private static final int BUTTON_H = 12;
    private static final int BUTTON_PAD = 2;
    private static final int COLOR_ENABLED = 0xFF44AA44;
    private static final int COLOR_DISABLED = 0xFF666666;
    private static final int COLOR_HOVER = 0xFF88CC88;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private final List<ButtonDef> buttons = new ArrayList<>();
    private boolean wasLeftPressed = false;
    private int reloadMessageTicks = 0;

    public CategoryFilterOverlay() {
        initButtons();

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("skyrecipes", "category_filter"),
                (graphics, tracker) -> render(graphics)
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void initButtons() {
        buttons.add(new ButtonDef("Cr", "categoryCraftingEnabled"));
        buttons.add(new ButtonDef("Fo", "categoryForgeEnabled"));
        buttons.add(new ButtonDef("Dr", "categoryDropsEnabled"));
        buttons.add(new ButtonDef("Npc", "categoryNpcShopEnabled"));
        buttons.add(new ButtonDef("Inf", "categoryNpcInfoEnabled"));
        buttons.add(new ButtonDef("Kat", "categoryKatUpgradeEnabled"));
        buttons.add(new ButtonDef("Tr", "categoryTradeEnabled"));
        buttons.add(new ButtonDef("Wiki", "categoryWikiInfoEnabled"));
        buttons.add(new ButtonDef("Es", "categoryEssenceEnabled"));
        buttons.add(new ButtonDef("Rf", "categoryReforgeEnabled"));
        buttons.add(new ButtonDef("Ga", "categoryGardenEnabled"));
    }

    private void render(GuiGraphicsExtractor graphics) {
        if (graphics == null) return;
        if (!SkyRecipesConfig.categoryButtonsVisible) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            // Don't render over screens (inventory, etc.) — only when overlay is open without a screen
            return;
        }

        // Check if RRV overlay is visible
        boolean rrvVisible;
        try {
            rrvVisible = cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay.INSTANCE.isEnabled();
        } catch (Exception e) {
            return; // RRV not loaded or overlay unavailable
        }

        if (!rrvVisible) {
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int totalW = buttons.size() * (BUTTON_W + BUTTON_PAD) - BUTTON_PAD;
        int startX = (screenW - totalW) / 2;
        int startY = 4; // Top of screen, just below potential boss bars

        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());

        for (int i = 0; i < buttons.size(); i++) {
            ButtonDef btn = buttons.get(i);
            int bx = startX + i * (BUTTON_W + BUTTON_PAD);
            int by = startY;
            boolean enabled = getConfigBoolean(btn.fieldName);
            boolean hovered = mouseX >= bx && mouseX < bx + BUTTON_W
                    && mouseY >= by && mouseY < by + BUTTON_H;

            int color = enabled ? (hovered ? COLOR_HOVER : COLOR_ENABLED) : COLOR_DISABLED;

            // Button background
            graphics.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, color);

            // Label
            String label = btn.label;
            int textW = mc.font.width(label);
            int tx = bx + (BUTTON_W - textW) / 2;
            int ty = by + 2;
            graphics.text(mc.font, label, tx, ty, COLOR_TEXT, false);
        }

        // Reload hint
        if (reloadMessageTicks > 0) {
            String msg = "Reload (F3+T) to apply category changes";
            int msgW = mc.font.width(msg);
            int mx = (screenW - msgW) / 2;
            int my = startY + BUTTON_H + 4;
            graphics.text(mc.font, msg, mx, my, 0xFFFFFF00, false);
        }
    }

    private void tick() {
        if (!SkyRecipesConfig.categoryButtonsVisible) {
            return;
        }

        if (reloadMessageTicks > 0) {
            reloadMessageTicks--;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean leftPressed = mc.mouseHandler.isLeftPressed();

        if (leftPressed && !wasLeftPressed) {
            // Mouse just clicked
            handleClick(mc);
        }

        wasLeftPressed = leftPressed;
    }

    private void handleClick(Minecraft mc) {
        if (mc.screen != null) {
            return;
        }

        boolean rrvVisible;
        try {
            rrvVisible = cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay.INSTANCE.isEnabled();
        } catch (Exception e) {
            return;
        }
        if (!rrvVisible) {
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int totalW = buttons.size() * (BUTTON_W + BUTTON_PAD) - BUTTON_PAD;
        int startX = (screenW - totalW) / 2;
        int startY = 4;

        double mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());

        for (int i = 0; i < buttons.size(); i++) {
            ButtonDef btn = buttons.get(i);
            int bx = startX + i * (BUTTON_W + BUTTON_PAD);
            int by = startY;
            if (mouseX >= bx && mouseX < bx + BUTTON_W
                    && mouseY >= by && mouseY < by + BUTTON_H) {
                toggleConfig(btn.fieldName);
                reloadMessageTicks = 60; // 3 seconds at 20 tps
                break;
            }
        }
    }

    private boolean getConfigBoolean(String fieldName) {
        try {
            Field field = SkyRecipesConfig.class.getField(fieldName);
            return (boolean) field.get(null);
        } catch (Exception e) {
            LOGGER.debug("Failed to read config field {}: {}", fieldName, e.getMessage());
            return true;
        }
    }

    private void toggleConfig(String fieldName) {
        try {
            Field field = SkyRecipesConfig.class.getField(fieldName);
            boolean current = (boolean) field.get(null);
            field.setBoolean(null, !current);
            SkyRecipesConfig.write("skyrecipes");
        } catch (Exception e) {
            LOGGER.debug("Failed to toggle config field {}: {}", fieldName, e.getMessage());
        }
    }

    private record ButtonDef(String label, String fieldName) {}
}
