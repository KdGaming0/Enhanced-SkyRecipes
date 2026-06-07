package com.github.kdgaming0.skyrecipes.core.render;

import cc.cassian.rrv.common.rendering.RrvGuiRenderHelper;
import com.github.kdgaming0.skyrecipes.SkyRecipes;
import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;
import com.github.kdgaming0.skyrecipes.core.mob.MobPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders a {@link MobPreview} into a centered preview box above the drop slot grid.
 * Adapted to fit RRV's entity_loot 32×32 preview area.
 */
public final class MobPreviewRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobPreviewRenderer.class);

    // RRV entity_loot preview box coordinates (relative to recipe card)
    public static final int BOX_LEFT = 70;
    public static final int BOX_TOP = 20;
    public static final int BOX_SIZE = 28;

    // Inner render bounds for vanilla entities (28×28, centered in the 32×32 box)
    private static final int RENDER_LEFT = 71;
    private static final int RENDER_TOP = 20;
    private static final int RENDER_SIZE = 26;

    private static final float VANILLA_BASE_SCALE = 10.0F;
    private static final float VANILLA_MAX_HEIGHT = 24.0F;
    private static final float VANILLA_ROT_X_DEG = 180.0F;

    private static final float SKULL_SCALE = 2.0F;

    private static final String PLACEHOLDER_GLYPH = "?";
    private static final int PLACEHOLDER_COLOR = 0xFFAAAAAA;

    private static final Set<String> LOGGED_SKIN_FAILURES = ConcurrentHashMap.newKeySet();

    private MobPreviewRenderer() {}

    /** Legacy single-entity render for reforge recipes. */
    public static void render(GuiGraphicsExtractor graphics, LivingEntity entity,
                              int x, int y, float scale, float rotationY) {
        if (entity == null || graphics == null) return;
        try {
            var dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
            @SuppressWarnings("unchecked")
            EntityRenderer<LivingEntity, EntityRenderState> renderer =
                    (EntityRenderer<LivingEntity, EntityRenderState>) dispatcher.getRenderer(entity);
            EntityRenderState state = renderer.createRenderState(entity, 1.0f);
            state.lightCoords = 15728880;
            if (state instanceof cc.cassian.rrv.common.rendering.IRrvWrappedRenderState wrapped) {
                wrapped.rrv$enableMultiRendering();
            }
            Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(rotationY));
            Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);
            int size = (int) (scale * 1.5f);
            graphics.entity(state, scale, translation, rotation, null,
                    x - size / 2, y - size / 2, x + size / 2, y + size / 2);
        } catch (Exception e) {
            LOGGER.debug("Failed to render mob preview: {}", e.getMessage());
        }
    }

    public static float getRotationAngle() {
        return (System.currentTimeMillis() % 20000L) / 20000.0f * 360.0f;
    }

    public static void render(MobPreview preview, GuiGraphicsExtractor gfx,
                              int recipeLeft, int recipeTop,
                              List<LivingEntity> entityStack,
                              int animTick, float partialTicks) {
        boolean rendered = switch (preview.kind()) {
            case VANILLA_ENTITY -> renderVanilla(topEntity(entityStack), gfx, recipeLeft, recipeTop, animTick, partialTicks);
            case PLAYER_WITH_SKIN -> renderPlayerSkin(preview.skinPath(), gfx, recipeLeft, recipeTop, animTick, partialTicks);
            case SKULL_ITEM -> renderSkull(preview.helmetItemId(), gfx, recipeLeft, recipeTop);
            case COMPOSITE -> renderComposite(preview, gfx, recipeLeft, recipeTop, entityStack, animTick, partialTicks);
        };

        if (!rendered) {
            renderPlaceholder(gfx, recipeLeft, recipeTop);
        }
    }

    @Nullable
    private static LivingEntity topEntity(List<LivingEntity> entityStack) {
        return entityStack.isEmpty() ? null : entityStack.get(0);
    }

    public static void renderPlaceholder(GuiGraphicsExtractor gfx, int recipeLeft, int recipeTop) {
        Font font = Minecraft.getInstance().font;
        int centerX = recipeLeft + BOX_LEFT + BOX_SIZE / 2;
        int centerY = recipeTop + BOX_TOP + BOX_SIZE / 2 - font.lineHeight / 2;
        int x = centerX - font.width(PLACEHOLDER_GLYPH) / 2;
        gfx.text(font, PLACEHOLDER_GLYPH, x, centerY, PLACEHOLDER_COLOR, false);
    }

    public static boolean isPointInPreviewBox(int mouseX, int mouseY) {
        return mouseX >= BOX_LEFT && mouseX < BOX_LEFT + BOX_SIZE
                && mouseY >= BOX_TOP && mouseY < BOX_TOP + BOX_SIZE;
    }

    // ── Composite ───────────────────────────────────────────────────────────────

    private static boolean renderComposite(MobPreview preview, GuiGraphicsExtractor gfx,
                                           int recipeLeft, int recipeTop,
                                           List<LivingEntity> entityStack,
                                           int animTick, float partialTicks) {
        List<MobPreview> layers = collectLayers(preview);

        boolean allVanilla = true;
        for (MobPreview layer : layers) {
            if (layer.skinPath() != null || layer.helmetItemId() != null) {
                allVanilla = false;
                break;
            }
        }

        if (allVanilla && !entityStack.isEmpty()) {
            return renderEntityStack(entityStack, gfx, recipeLeft, recipeTop, animTick, partialTicks);
        }

        return renderMixedStack(layers, entityStack, gfx, recipeLeft, recipeTop, animTick, partialTicks);
    }

    private static List<MobPreview> collectLayers(MobPreview preview) {
        List<MobPreview> layers = new ArrayList<>();
        MobPreview current = preview;
        while (current != null) {
            layers.add(current);
            if (current.kind() == MobPreview.Kind.COMPOSITE) {
                current = current.rider();
            } else {
                current = null;
            }
        }
        return layers;
    }

    private static boolean renderEntityStack(List<LivingEntity> entityStack, GuiGraphicsExtractor gfx,
                                             int recipeLeft, int recipeTop,
                                             int animTick, float partialTicks) {
        if (entityStack.isEmpty()) return false;

        double stackBottom = Double.MAX_VALUE;
        double stackTop = -Double.MAX_VALUE;
        for (LivingEntity entity : entityStack) {
            AABB bb = entity.getBoundingBox();
            stackBottom = Math.min(stackBottom, bb.minY);
            stackTop = Math.max(stackTop, bb.maxY);
        }
        double stackHeight = Math.max(1.0, stackTop - stackBottom);
        double stackCenter = (stackBottom + stackTop) / 2.0;

        float scale = VANILLA_BASE_SCALE;
        if (stackHeight * scale > VANILLA_MAX_HEIGHT) {
            scale = (float) (VANILLA_MAX_HEIGHT / stackHeight);
        }

        int x0 = recipeLeft + RENDER_LEFT;
        int y0 = recipeTop + RENDER_TOP;
        int x1 = x0 + RENDER_SIZE;
        int y1 = y0 + RENDER_SIZE;

        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(VANILLA_ROT_X_DEG),
                (animTick + partialTicks) / 180.0F * Mth.PI,
                0.0F);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean anyRendered = false;

        for (LivingEntity entity : entityStack) {
            @SuppressWarnings("unchecked")
            EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
            EntityRenderState state = renderer.createRenderState(entity, 1.0F);
            state.lightCoords = 15728880;
            if (state instanceof cc.cassian.rrv.common.rendering.IRrvWrappedRenderState wrapped) {
                wrapped.rrv$enableMultiRendering();
            }

            float translationY = (float) (stackCenter - entity.getY());

            gfx.entity(state, scale,
                    new Vector3f(0.0F, translationY, 0.0F),
                    rotation, null, x0, y0, x1, y1);
            anyRendered = true;
        }

        return anyRendered;
    }

    private static boolean renderMixedStack(List<MobPreview> layers, List<LivingEntity> entityStack,
                                            GuiGraphicsExtractor gfx, int recipeLeft, int recipeTop,
                                            int animTick, float partialTicks) {
        double stackBottom = Double.MAX_VALUE;
        double stackTop = -Double.MAX_VALUE;
        for (LivingEntity entity : entityStack) {
            AABB bb = entity.getBoundingBox();
            stackBottom = Math.min(stackBottom, bb.minY);
            stackTop = Math.max(stackTop, bb.maxY);
        }
        double stackHeight = Math.max(1.0, stackTop - stackBottom);
        float uniformScale = VANILLA_BASE_SCALE;
        if (stackHeight * uniformScale > VANILLA_MAX_HEIGHT) {
            uniformScale = (float) (VANILLA_MAX_HEIGHT / stackHeight);
        }

        boolean anyOk = false;
        int entityIdx = 0;
        int cumulativeYOffset = 0;

        for (MobPreview layer : layers) {
            @Nullable LivingEntity entity = null;
            if (layer.entityType() != null && entityIdx < entityStack.size()) {
                entity = entityStack.get(entityIdx++);
            }

            if (entity != null && entityIdx > 1) {
                LivingEntity prev = entityStack.get(entityIdx - 2);
                double prevCenter = prev.getBoundingBox().getCenter().y;
                double currCenter = entity.getBoundingBox().getCenter().y;
                int pixelOffset = (int) Math.round((currCenter - prevCenter) * uniformScale);
                cumulativeYOffset -= pixelOffset;
            } else if (entity == null && entityIdx > 0) {
                LivingEntity prev = entityStack.get(entityIdx - 1);
                double prevCenter = prev.getBoundingBox().getCenter().y;
                double prevHeight = prev.getBoundingBox().getYsize();
                double approxRiderCenter = prevCenter + prevHeight / 2.0 + 0.6;
                int pixelOffset = (int) Math.round((approxRiderCenter - prevCenter) * uniformScale);
                cumulativeYOffset -= pixelOffset;
            }

            int yBase = BOX_TOP + cumulativeYOffset;

            if (layer.skinPath() != null) {
                anyOk |= renderPlayerSkin(layer.skinPath(), gfx, recipeLeft, recipeTop, animTick, partialTicks, yBase);
            } else if (layer.helmetItemId() != null) {
                anyOk |= renderSkull(layer.helmetItemId(), gfx, recipeLeft, recipeTop, yBase);
            } else if (entity != null) {
                anyOk |= renderVanilla(entity, gfx, recipeLeft, recipeTop, animTick, partialTicks, yBase, uniformScale);
            }
        }

        return anyOk;
    }

    // ── Vanilla-entity path ─────────────────────────────────────────────────────

    private static boolean renderVanilla(@Nullable LivingEntity entity, GuiGraphicsExtractor gfx,
                                         int recipeLeft, int recipeTop,
                                         int animTick, float partialTicks) {
        return renderVanilla(entity, gfx, recipeLeft, recipeTop, animTick, partialTicks, BOX_TOP, -1.0F);
    }

    private static boolean renderVanilla(@Nullable LivingEntity entity, GuiGraphicsExtractor gfx,
                                         int recipeLeft, int recipeTop,
                                         int animTick, float partialTicks, int yBase,
                                         float overrideScale) {
        if (entity == null) return false;

        float scale = overrideScale > 0 ? overrideScale : VANILLA_BASE_SCALE;
        if (overrideScale <= 0.0F) {
            AABB bb = entity.getBoundingBox();
            if (bb.getYsize() * scale > VANILLA_MAX_HEIGHT) {
                scale = (float) (VANILLA_MAX_HEIGHT / bb.getYsize());
            }
        }

        int x0 = recipeLeft + RENDER_LEFT;
        int y0 = recipeTop + yBase + (BOX_SIZE - RENDER_SIZE) / 2;
        int x1 = x0 + RENDER_SIZE;
        int y1 = y0 + RENDER_SIZE;

        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(VANILLA_ROT_X_DEG),
                (animTick + partialTicks) / 180.0F * Mth.PI,
                0.0F);

        RrvGuiRenderHelper.renderEntityOnScreen(
                gfx, entity, x0, y0, x1, y1, scale,
                new Vector3f(0.0F, RENDER_SIZE / scale / 2.0F, 0.0F),
                rotation, null);
        return true;
    }

    // ── Player-skin path ────────────────────────────────────────────────────────

    private static boolean renderPlayerSkin(String skinPath, GuiGraphicsExtractor gfx,
                                            int recipeLeft, int recipeTop,
                                            int animTick, float partialTicks) {
        return renderPlayerSkin(skinPath, gfx, recipeLeft, recipeTop, animTick, partialTicks, BOX_TOP);
    }

    private static boolean renderPlayerSkin(String skinPath, GuiGraphicsExtractor gfx,
                                            int recipeLeft, int recipeTop,
                                            int animTick, float partialTicks, int yBase) {
        Identifier texture = MobSkinRegistry.getOrLoad(skinPath);
        if (texture == null) {
            if (LOGGED_SKIN_FAILURES.add(skinPath)) {
                LOGGER.warn("Player skin texture failed to load for '{}' — showing placeholder.", skinPath);
            }
            return false;
        }

        int x0 = recipeLeft + BOX_LEFT;
        int y0 = recipeTop + yBase;

        PlayerSkinRenderer.render(gfx, texture, x0, y0, BOX_SIZE, BOX_SIZE, animTick + partialTicks);
        return true;
    }

    // ── Skull path ──────────────────────────────────────────────────────────────

    private static boolean renderSkull(String helmetItemId, GuiGraphicsExtractor gfx,
                                       int recipeLeft, int recipeTop) {
        return renderSkull(helmetItemId, gfx, recipeLeft, recipeTop, BOX_TOP);
    }

    private static boolean renderSkull(String helmetItemId, GuiGraphicsExtractor gfx,
                                       int recipeLeft, int recipeTop, int yBase) {
        ItemRegistry registry = SkyRecipes.getItemRegistry();
        if (registry == null) return false;

        NeuItem item = registry.getByInternalName(helmetItemId).orElse(null);
        if (item == null) return false;

        ItemStack stack = ItemStackBuilder.build(item);
        if (stack.isEmpty()) return false;

        int centerX = recipeLeft + BOX_LEFT + (BOX_SIZE - 16) / 2;
        int centerY = recipeTop + yBase + (BOX_SIZE - 16) / 2;

        gfx.pose().pushMatrix();
        gfx.pose().identity();
        gfx.pose().translate(centerX + 8, centerY + 8);
        gfx.pose().scale(SKULL_SCALE, SKULL_SCALE);
        gfx.pose().translate(-8, -8);
        gfx.item(stack, 0, 0);
        gfx.pose().popMatrix();
        return true;
    }
}
