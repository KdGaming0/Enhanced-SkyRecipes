package com.github.kdgaming0.skyrecipes.rrv.overlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Coalesces the item-list dim overlay's per-slot quads into horizontal runs.
 *
 * <p><b>Why:</b> every {@code guiGraphics.fill} becomes a separate GUI element, and MC 26.1's
 * render-state builder inserts each one with
 * {@code addGuiElement → findAppropriateNode → navigateToAboveHighestElementWithIntersectingBounds
 * → hasIntersection}, which scans the elements already queued to find the insertion point. Cost
 * per element therefore grows with the number of elements, so the total is superlinear —
 * {@code hasIntersection} is the largest non-item self-time frame in the profile (~1.96%
 * aggregate across all mods). A 54-slot chest with a narrow query emits ~50 separate dim quads
 * per frame; merging contiguous ones typically leaves 8–12.</p>
 *
 * <p><b>How:</b> slots arrive in the order the container built them, which for vanilla and
 * essentially every modded container is row-major. Rather than sort (per-frame allocation), this
 * extends the open run whenever the next slot continues it exactly — same {@code y}, and {@code x}
 * landing on the current run's right edge — and flushes otherwise. A container that hands out
 * slots in some other order simply breaks runs more often and degrades to one quad per slot,
 * which is what the code did before. Output pixels are identical either way: merged runs cover
 * exactly the union of the slots they replace, and the fill colour is uniform.</p>
 *
 * <p>Runs are horizontal only. Merging vertically as well would need the full rectangle-union
 * problem and a sort, for a much smaller marginal gain.</p>
 *
 * <p>Render-thread only, and one instance per render pass — {@link #flush} must be called before
 * the pose matrix is popped or the trailing run is lost.</p>
 */
public final class DimQuadEmitter {

    private static final int NO_RUN = Integer.MIN_VALUE;

    private final int size;
    private final int color;

    private int runY = NO_RUN;
    private int runX1;
    private int runX2;

    public DimQuadEmitter(int size, int color) {
        this.size = size;
        this.color = color;
    }

    /** Adds one slot-sized quad at {@code (x, y)}, extending the open run when it is contiguous. */
    public void add(GuiGraphicsExtractor guiGraphics, int x, int y) {
        if (runY == y && runX2 == x) {
            runX2 = x + size;
            return;
        }
        flush(guiGraphics);
        runY = y;
        runX1 = x;
        runX2 = x + size;
    }

    /** Emits the open run, if any. Safe to call repeatedly. */
    public void flush(GuiGraphicsExtractor guiGraphics) {
        if (runY == NO_RUN) {
            return;
        }
        guiGraphics.fill(runX1, runY, runX2, runY + size, color);
        runY = NO_RUN;
    }
}
