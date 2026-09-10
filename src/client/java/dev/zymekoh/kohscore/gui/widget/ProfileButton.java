package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Compact, scrollable action card. Full details remain available at large GUI scales. */
public final class ProfileButton extends CoreControl {
	private final Runnable action;
	public ProfileButton(int x, int y, int width, int height, Component title, Component description, Runnable action) {
		super(x, y, width, height, title);
		this.action = action;
		setDescription(description);
		setTooltip(Tooltip.create(description));
		setTooltipDelay(java.time.Duration.ofMillis(350));
	}
	@Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active) action.run();
	}
	@Override public boolean keyPressed(KeyEvent event) {
		if (active && isFocused() && event.isSelection()) { action.run(); return true; }
		return super.keyPressed(event);
	}
	@Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mx, int my, float dt) {
		float hover = updateHover();
		float alpha = alphaFactor() * (active ? 1 : LOCKED_FADE);
		CoreDraw.panel(graphics, getX(), getY(), getWidth(), getHeight(), 4,
				CoreTheme.fade(CoreTheme.blend(CoreTheme.PANEL_LIGHT, CoreTheme.PANEL_HOVER, hover), alpha),
				CoreTheme.fade(CoreTheme.blend(CoreTheme.BORDER_SOFT, CoreTheme.ACCENT_SOFT, hover), alpha));
		var font = Minecraft.getInstance().font;
		int available = Math.max(1, getWidth() - 12);
		CoreDraw.clippedText(graphics, font, getMessage(), getX() + 6, getY() + 5, available,
				CoreTheme.fade(CoreTheme.TEXT, alpha));
		int y = getY() + 18;
		for (var line : font.split(description(), available)) {
			if (y + font.lineHeight > getY() + getHeight() - 3) break;
			graphics.text(font, line, getX() + 6, y, CoreTheme.fade(CoreTheme.TEXT_MUTED, alpha));
			y += font.lineHeight + 1;
		}
	}
	@Override protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
		output.add(NarratedElementType.USAGE, description());
	}
}
