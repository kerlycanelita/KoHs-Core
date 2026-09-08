package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One entry in the left-hand tab rail.
 *
 * <p>The selected tab is marked with a bar on its leading edge rather than a different fill, which
 * keeps the selection legible even when the tab is also being hovered.
 */
public final class TabButton extends CoreControl {

	private final BooleanSupplier selected;
	private final Consumer<TabButton> onPress;
	private final int accentColor;
	private net.minecraft.resources.Identifier icon;
	private boolean compact;

	public void setIcon(net.minecraft.resources.Identifier icon) {
		this.icon = icon;
	}

	/**
	 * Collapses the tab to its icon.
	 *
	 * <p>On a narrow window the rail keeps shrinking until the label is a two-letter stub, which
	 * tells the player nothing and still eats the width the content pane needs. A centred icon in a
	 * narrow rail is both more legible and cheaper in horizontal space.
	 *
	 * <p>A tooltip carries the name in this mode. The description panel cannot be relied on: it is
	 * the first thing the layout drops on a short window, which is exactly when compact kicks in.
	 */
	public void setCompact(boolean compact) {
		this.compact = compact;
		if (compact) {
			this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(this.getMessage()));
			this.setTooltipDelay(java.time.Duration.ofMillis(160));
		} else {
			this.setTooltip(null);
		}
	}

	public TabButton(int x, int y, int width, int height, Component label, Component description,
			BooleanSupplier selected, Consumer<TabButton> onPress, int accentColor) {
		super(x, y, width, height, label);
		this.selected = selected;
		this.onPress = onPress;
		this.accentColor = accentColor == 0 ? CoreTheme.ACCENT : accentColor;
		this.setDescription(description);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (this.active) {
			this.onPress.accept(this);
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float hover = this.updateHover();
		float alpha = this.alphaFactor();
		boolean isSelected = this.selected.getAsBoolean();

		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();

		// Flat unless it is selected or under the cursor. A rail of eight bordered boxes reads as a
		// table of its own, which is the opposite of what navigation should do.
		if (isSelected) {
			CoreDraw.roundedRect(graphics, x, y, width, height, 4,
					CoreTheme.fade(CoreTheme.withAlpha(CoreTheme.PANEL_SELECTED, 210), alpha));
		} else if (hover > 0.01F) {
			CoreDraw.roundedRect(graphics, x, y, width, height, 4,
					CoreTheme.fade(CoreTheme.withAlpha(CoreTheme.ROW_HOVER, Math.round(150 * hover)), alpha));
		}

		// The accent bar carries the selection, so no border is needed to say which tab is open.
		if (isSelected || hover > 0.01F) {
			int barAlpha = isSelected ? 255 : Math.round(150 * hover);
			int barInset = Math.max(2, height / 5);
			graphics.fill(x, y + barInset, x + 2, y + height - barInset,
					CoreTheme.fade(CoreTheme.withAlpha(this.accentColor, barAlpha), alpha));
		}

		int textColor = isSelected ? CoreTheme.TEXT : CoreTheme.blend(CoreTheme.TEXT_MUTED, CoreTheme.TEXT, hover);
		int textY = y + (height - 8) / 2;
		int iconSize = Math.max(8, Math.min(16, height - 2));
		int textX = x + 9;
		int iconX = x + 5;

		if (this.compact) {
			// Centre the icon in the whole button rather than left-aligning it against a label that
			// is no longer drawn.
			if (this.icon != null) {
				graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.icon,
						x + (width - iconSize) / 2, y + (height - iconSize) / 2, 0, 0, iconSize, iconSize,
						64, 64, 64, 64, CoreTheme.fade(0xFFFFFFFF, alpha));
			} else {
				// No icon to fall back on, so show the first letter instead of nothing at all.
				String initial = this.getMessage().getString();
				if (!initial.isEmpty()) {
					graphics.centeredText(Minecraft.getInstance().font,
							Component.literal(initial.substring(0, 1)), x + width / 2, textY,
							CoreTheme.fade(textColor, alpha));
				}
			}
			return;
		}

		if (this.icon != null) {
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.icon,
					iconX, y + (height - iconSize) / 2, 0, 0, iconSize, iconSize, 64, 64, 64, 64,
					CoreTheme.fade(0xFFFFFFFF, alpha));
			textX = x + iconSize + 9;
		}
		CoreDraw.clippedText(graphics, Minecraft.getInstance().font, this.getMessage(), textX, textY, Math.max(1, x + width - 5 - textX),
				CoreTheme.fade(textColor, alpha));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, this.getMessage());
	}
}
