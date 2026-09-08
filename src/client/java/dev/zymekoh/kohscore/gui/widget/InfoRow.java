package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;

public final class InfoRow extends CoreControl {
    public InfoRow(int x, int y, int width, Component message) { super(x, y, width, 66, message); }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        int y = getY() + 4;
        var font = Minecraft.getInstance().font;
        for (var line : font.split(getMessage(), Math.max(1, getWidth() - 8))) {
            if (y + font.lineHeight > getY() + getHeight()) break;
            g.text(font, line, getX() + 4, y, CoreTheme.TEXT_MUTED);
            y += font.lineHeight + 1;
        }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE, getMessage()); }
}
