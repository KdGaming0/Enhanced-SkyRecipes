package com.github.kdgaming0.skyrecipes.core.render;

import com.github.kdgaming0.skyrecipes.core.mob.MobPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the entity lifecycle, animation, and rendering for a drop-recipe mob preview.
 */
public class MobPreviewController {

    private static final int ROTATION_PERIOD = 360;

    private final MobPreview preview;
    private final List<LivingEntity> entityStack = new ArrayList<>();

    private int animationTick;
    private boolean hovered;

    public MobPreviewController(@Nullable MobPreview preview) {
        this.preview = preview;
    }

    @Nullable
    private static LivingEntity spawnForLayer(ClientLevel level, @Nullable EntityType<?> type) {
        if (type == null) return null;
        Entity entity = type.create(level, EntitySpawnReason.LOAD);
        if (!(entity instanceof LivingEntity living)) return null;
        living.setYBodyRot(30.0F);
        living.setYHeadRot(30.0F);
        return living;
    }

    /**
     * Legacy static helper for reforge recipes.
     */
    public static LivingEntity createVillager() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            var entity = net.minecraft.world.entity.EntityType.VILLAGER.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (entity instanceof LivingEntity living) {
                living.setPos(0, 0, 0);
                living.yRotO = 0;
                living.setYRot(0);
                return living;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Legacy static helper for reforge recipes.
     */
    public static void disposeEntity(LivingEntity entity) {
        if (entity != null) {
            try {
                entity.remove(Entity.RemovalReason.DISCARDED);
            } catch (Exception ignored) {
            }
        }
    }

    public void init() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (preview == null || !preview.needsLivingEntity()) return;
        spawnRecursive(level, preview, null);
    }

    private void spawnRecursive(ClientLevel level, MobPreview current, @Nullable LivingEntity mount) {
        LivingEntity entity = spawnForLayer(level, current.entityType());
        if (entity != null) {
            if (mount != null) {
                entity.startRiding(mount);
                mount.positionRider(entity);
            }
            entityStack.add(entity);
        }
        if (current.rider() != null) {
            spawnRecursive(level, current.rider(), entity != null ? entity : mount);
        }
    }

    public void fade() {
        for (LivingEntity entity : entityStack) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
        entityStack.clear();
    }

    public void tick() {
        if (!hovered) {
            animationTick++;
            if (animationTick >= ROTATION_PERIOD) animationTick = 0;
        }
    }

    public void render(GuiGraphicsExtractor gfx, int recipeLeft, int recipeTop,
                       int mouseX, int mouseY, float partialTicks) {
        hovered = MobPreviewRenderer.isPointInPreviewBox(mouseX, mouseY);
        syncPassengerPositions();

        if (preview != null) {
            MobPreviewRenderer.render(preview, gfx, recipeLeft, recipeTop, entityStack, animationTick, partialTicks);
        } else {
            MobPreviewRenderer.renderPlaceholder(gfx, recipeLeft, recipeTop);
        }
    }

    private void syncPassengerPositions() {
        for (int i = 0; i < entityStack.size() - 1; i++) {
            LivingEntity mount = entityStack.get(i);
            LivingEntity rider = entityStack.get(i + 1);
            if (rider.getVehicle() == mount) {
                mount.positionRider(rider);
            }
        }
    }

    public boolean isHovered() {
        return hovered;
    }

    @Nullable
    public MobPreview getPreview() {
        return preview;
    }
}
