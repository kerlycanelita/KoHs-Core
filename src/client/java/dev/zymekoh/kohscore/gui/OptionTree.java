package dev.zymekoh.kohscore.gui;

import dev.zymekoh.kohscore.gui.widget.CoreControl;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

/** Height, slide and fade transition for dependent rows; never rebuilds widgets on toggle. */
public final class OptionTree {
    private static final class Branch {
        BooleanSupplier condition;
        int depth;
        float reveal;
        boolean baseActive;
        Branch(BooleanSupplier condition, int depth, boolean baseActive) {
            this.condition = condition;
            this.depth = depth;
            this.reveal = condition.getAsBoolean() ? 1 : 0;
            this.baseActive = baseActive;
        }
    }
    private final Map<AbstractWidget, Branch> branches = new IdentityHashMap<>();
    private long lastFrame = System.nanoTime();
    public void clear() { branches.clear(); lastFrame = System.nanoTime(); }
    public void depend(AbstractWidget widget, BooleanSupplier condition, int depth) {
        branches.put(widget, new Branch(condition, depth, widget.active));
    }
    public int layout(GuiGraphicsExtractor g, List<AbstractWidget> widgets, int x, int top,
            int width, int height, int gap, double scroll) {
        long now = System.nanoTime();
        float dt = Math.min(0.05F, (now - lastFrame) / 1_000_000_000.0F);
        lastFrame = now;
        float cursor = top - (float) scroll;
        float total = 0;
        for (AbstractWidget widget : widgets) {
            Branch branch = branches.get(widget);
            float reveal = 1;
            int indent = 0;
            if (branch != null) {
                boolean locked = widget instanceof CoreControl control && control.isLocked();
                float target = branch.condition.getAsBoolean() || locked ? 1 : 0;
                branch.reveal += (target - branch.reveal) * (1 - (float) Math.exp(-18 * dt));
                if (Math.abs(target - branch.reveal) < 0.008F) branch.reveal = target;
                reveal = branch.reveal;
                indent = Math.min(width / 5, branch.depth * 10);
            }
            float occupied = (widget.getHeight() + gap) * reveal;
            int y = Math.round(cursor - (1 - reveal) * 7);
            widget.setX(x + indent + Math.round((1 - reveal) * 8));
            widget.setWidth(Math.max(1, width - indent - Math.round((1 - reveal) * 8)));
            widget.setY(y);
            // The row becomes clickable only after it occupies its whole visual rectangle.
            boolean inViewport = y >= top && y + widget.getHeight() <= top + height;
            // Keep the row drawable while it enters/leaves so CoreControl's alpha can produce a
            // genuine fade. It becomes interactive only after the slide has settled and it is
            // entirely inside the viewport.
            widget.visible = reveal > 0.03F && inViewport;
            if (branch != null) widget.active = branch.baseActive && reveal > 0.98F && inViewport;
            if (widget instanceof CoreControl control) control.setRevealAlpha(reveal);
            if (!widget.visible || reveal <= 0.03F) widget.setFocused(false);
            if (indent > 0 && reveal > 0.03F && y >= top && y + widget.getHeight() <= top + height) {
                int bx = x + indent - 5;
                int c = CoreTheme.fade(CoreTheme.ACCENT_SOFT, reveal * 0.6F);
                g.fill(bx, y, bx + 1, y + widget.getHeight() / 2, c);
                g.fill(bx, y + widget.getHeight() / 2, x + indent - 1, y + widget.getHeight() / 2 + 1, c);
            }
            if (reveal == 0) widget.setFocused(false);
            cursor += occupied;
            total += occupied;
        }
        return Math.round(total);
    }
}
