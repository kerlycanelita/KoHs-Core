package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Puts a string on the system clipboard.
 *
 * <p>Used for the JVM profiles, which exist to be pasted into a launcher. Reading two dozen flags
 * off the screen and retyping them is exactly the kind of transcription nobody gets right first
 * time, so the flags are never presented without a way to take them.
 *
 * <p>The label swaps to a confirmation for a moment after a successful copy. Without that, clicking
 * produces no visible change at all and the natural response is to click again.
 */
public final class CopyButton extends CoreControl {

	private static final long CONFIRMATION_MILLIS = 1_400L;

	private static final Component LABEL = Component.translatable("kohs_core.button.copy");
	private static final Component CONFIRMATION = Component.translatable("kohs_core.button.copied");

	private final Supplier<String> payload;
	private long copiedAtMillis = Long.MIN_VALUE;

	public CopyButton(int x, int y, int width, int height, Component description, Supplier<String> payload) {
		super(x, y, width, height, LABEL);
		this.payload = payload;
		this.setDescription(description);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!this.active) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.keyboardHandler == null) {
			return;
		}
		String text = this.payload.get();
		if (text == null || text.isEmpty()) {
			return;
		}
		minecraft.keyboardHandler.setClipboard(text);
		this.copiedAtMillis = System.currentTimeMillis();
	}

	private boolean showingConfirmation() {
		return System.currentTimeMillis() - this.copiedAtMillis < CONFIRMATION_MILLIS;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float hover = this.updateHover();
		float alpha = this.alphaFactor();
		boolean confirmed = this.showingConfirmation();

		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();

		int fill = confirmed
				? CoreTheme.withAlpha(CoreTheme.GOOD, 60)
				: CoreTheme.blend(CoreTheme.PANEL_LIGHT, CoreTheme.PANEL_HOVER, hover);
		int border = confirmed
				? CoreTheme.withAlpha(CoreTheme.GOOD, 190)
				: CoreTheme.blend(CoreTheme.BORDER_SOFT, CoreTheme.ACCENT_SOFT, hover);
		CoreDraw.panel(graphics, x, y, width, height, 4, CoreTheme.fade(fill, alpha),
				CoreTheme.fade(border, alpha));

		Component label = confirmed ? CONFIRMATION : LABEL;
		int textColor = confirmed ? CoreTheme.GOOD : CoreTheme.blend(CoreTheme.TEXT_MUTED, CoreTheme.TEXT, hover);
		int textY = y + Math.max(1, (height - Minecraft.getInstance().font.lineHeight) / 2);

		// Centre the label, but fall back to clipping rather than letting it spill past the border
		// when the pane is too narrow for the full word.
		int available = Math.max(1, width - 6);
		int labelWidth = Minecraft.getInstance().font.width(label);
		if (labelWidth <= available) {
			graphics.centeredText(Minecraft.getInstance().font, label, x + width / 2, textY,
					CoreTheme.fade(textColor, alpha));
		} else {
			CoreDraw.clippedText(graphics, Minecraft.getInstance().font, label, x + 3, textY, available,
					CoreTheme.fade(textColor, alpha));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, this.showingConfirmation() ? CONFIRMATION : LABEL);
		output.add(NarratedElementType.USAGE, this.description());
	}
}
