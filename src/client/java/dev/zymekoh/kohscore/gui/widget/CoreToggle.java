package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** An animated on/off switch, with the knob sliding between the two ends of a track. */
public final class CoreToggle extends CoreControl {

	private static final int TRACK_WIDTH = 26;
	private static final int TRACK_HEIGHT = 12;

	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;

	private float knobPosition;
	private long lastFrameMillis = System.nanoTime() / 1_000_000L;

	public CoreToggle(int x, int y, int width, int height, Component label, Component description,
			BooleanSupplier getter, Consumer<Boolean> setter) {
		super(x, y, width, height, label);
		this.getter = getter;
		this.setter = setter;
		this.setDescription(description);
		this.knobPosition = getter.getAsBoolean() ? 1.0F : 0.0F;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!this.active) {
			return;
		}
		this.setter.accept(!this.getter.getAsBoolean());
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float hover = this.updateHover();
		float alpha = this.alphaFactor();

		long now = System.nanoTime() / 1_000_000L;
		float elapsed = Math.min(50L, Math.max(0L, now - this.lastFrameMillis)) / 1000.0F;
		this.lastFrameMillis = now;
		float target = this.getter.getAsBoolean() ? 1.0F : 0.0F;
		this.knobPosition += (target - this.knobPosition) * CoreTheme.approach(elapsed, 16.0F);

		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();

		CoreDraw.rowSurface(graphics, x, y, width, height, hover, alpha, this.isLastRow());

		// Label on the left, switch pinned to the right edge.
		int textY = y + (height - 8) / 2;
		int trackX = x + width - TRACK_WIDTH - 8;
		int textX = x + 10;
		CoreDraw.clippedText(graphics, net.minecraft.client.Minecraft.getInstance().font, this.getMessage(),
				textX, textY, Math.max(1, trackX - textX - 8), CoreTheme.fade(CoreTheme.TEXT, alpha));

		int trackY = y + (height - TRACK_HEIGHT) / 2;
		int trackColor = CoreTheme.blend(0xFF2A1E38, CoreTheme.ACCENT_DEEP, this.knobPosition);
		CoreDraw.roundedRect(graphics, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2,
				CoreTheme.fade(trackColor, alpha));

		int knobSize = TRACK_HEIGHT - 4;
		int travel = TRACK_WIDTH - knobSize - 4;
		int knobX = trackX + 2 + Math.round(travel * Mth.clamp(this.knobPosition, 0.0F, 1.0F));
		CoreDraw.roundedRect(graphics, knobX, trackY + 2, knobSize, knobSize, knobSize / 2,
				CoreTheme.fade(0xFFF1E8FA, alpha));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, this.getMessage());
		output.add(NarratedElementType.USAGE, this.getter.getAsBoolean()
				? Component.translatable("options.on")
				: Component.translatable("options.off"));
	}
}
