package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import dev.zymekoh.kohscore.orchestration.SuiteCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A keyboard-accessible settings gear beside a persistent description card. */
public final class SuiteCard extends CoreControl {
    private final SuiteCatalog.Entry entry;
    private final Screen parent;
    private boolean failed;
    public SuiteCard(int x, int y, int width, SuiteCatalog.Entry entry, Screen parent) {
        super(x, y, width, 76, Component.literal(entry.name()));
        this.entry = entry;
        this.parent = parent;
        this.active = entry.configurable();
        this.setDescription(entry.description());
        this.setTooltip(Tooltip.create(entry.status()));
    }
    private int gearX() { return this.getX() + this.getWidth() - 29; }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= gearX() && event.y() < getY() + 29) this.open();
    }
    private void open() {
        if (this.active && !entry.open(parent)) {
            this.failed = true;
            this.active = false;
            this.setTooltip(Tooltip.create(Component.translatable("kohs_core.suite.failed")));
        }
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (this.isFocused() && this.active && event.isSelection()) { this.open(); return true; }
        return super.keyPressed(event);
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth();
        CoreDraw.panel(g, x, y, w, getHeight() - 3, 5, CoreTheme.PANEL_DEEP, CoreTheme.BORDER_SOFT);
        CoreDraw.clippedText(g, font, getMessage(), x + 7, y + 8, Math.max(1, w - 42), CoreTheme.TEXT);
        int cx = gearX() + 12, cy = y + 14;
        boolean hover = active && (isFocused() || mouseX >= gearX() && mouseX < x + w
                && mouseY >= y && mouseY < y + 29);
        int color = active ? (hover ? CoreTheme.ACCENT_BRIGHT : CoreTheme.TEXT_MUTED) : CoreTheme.TEXT_DISABLED;
        double rotation = hover ? System.nanoTime() / 500_000_000.0 : 0;
        for (int tooth = 0; tooth < 8; tooth++) {
            double angle = tooth * Math.PI / 4 + rotation;
            int px = cx + (int) Math.round(Math.cos(angle) * 7);
            int py = cy + (int) Math.round(Math.sin(angle) * 7);
            g.fill(px - 1, py - 1, px + 2, py + 2, color);
        }
        CoreDraw.roundedRect(g, cx - 6, cy - 6, 12, 12, 5, color);
        CoreDraw.roundedRect(g, cx - 2, cy - 2, 4, 4, 2, CoreTheme.PANEL_DEEP);
        int lineY = y + 29;
        var lines = font.split(entry.description(), Math.max(1, w - 14));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), x + 7, lineY, CoreTheme.TEXT_MUTED);
            lineY += font.lineHeight + 1;
        }
        Component status = failed ? Component.translatable("kohs_core.suite.failed")
                : entry.installed() ? Component.literal(entry.version()).append(" · ").append(entry.status())
                : entry.status();
        CoreDraw.clippedText(g, font, status, x + 7, y + 60, Math.max(1, w - 14),
                entry.installed() ? CoreTheme.ACCENT_SOFT : CoreTheme.TEXT_DISABLED);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.HINT, entry.status());
    }
}
