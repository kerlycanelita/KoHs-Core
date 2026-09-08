package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * A numeric slider that shows its own value in the label.
 *
 * <p>Steps are applied on the value, not on the pixel position, so a slider from 0 to 512 in steps
 * of 16 lands on exact multiples no matter how wide the window is.
 */
public final class CoreSlider extends CoreControl {

	private final double min;
	private final double max;
	private final double step;
	private final DoubleSupplier getter;
	private final Consumer<Double> setter;
	private final DoubleFunction<Component> formatter;

	private boolean dragging;

	public CoreSlider(int x, int y, int width, int height, Component label, Component description, double min,
			double max, double step, DoubleSupplier getter, Consumer<Double> setter,
			DoubleFunction<Component> formatter) {
		super(x, y, width, height, label);
		this.min = min;
		this.max = max;
		this.step = step <= 0.0 ? 0.0 : step;
		this.getter = getter;
		this.setter = setter;
		this.formatter = formatter;
		this.setDescription(description);
	}

	/** Current value mapped to 0..1. */
	private double normalised() {
		double range = this.max - this.min;
		return range <= 0.0 ? 0.0 : Mth.clamp((this.getter.getAsDouble() - this.min) / range, 0.0, 1.0);
	}

	private void applyFromMouse(double mouseX) {
		// Must match the drawn track exactly, or the value you get differs from the point you clicked.
		int trackX = this.getX() + 10;
		int trackWidth = Math.max(1, this.getWidth() - 20);
		double fraction = Mth.clamp((mouseX - trackX) / trackWidth, 0.0, 1.0);
		this.applyValue(this.min + fraction * (this.max - this.min));
	}

	private void applyValue(double raw) {
		double value = raw;
		if (this.step > 0.0) {
			value = this.min + Math.round((value - this.min) / this.step) * this.step;
		}
		value = Mth.clamp(value, this.min, this.max);
		// Drag events arrive far more often than a stepped slider's value changes. Avoid firing an
		// expensive renderer update, texture reload request or disk save for the same value dozens
		// of times in one frame.
		if (Math.abs(value - this.getter.getAsDouble()) > 1.0E-9) {
			this.setter.accept(value);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!this.active) {
			return;
		}
		this.dragging = true;
		this.applyFromMouse(event.x());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (this.active && this.dragging) {
			this.applyFromMouse(event.x());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!this.active) {
			return false;
		}
		// Arrow keys nudge by one step, which is the only way to hit an exact value on a wide range.
		int nudge = switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT -> -1;
			case GLFW.GLFW_KEY_RIGHT -> 1;
			default -> 0;
		};
		if (nudge != 0) {
			double increment = this.step > 0.0 ? this.step : (this.max - this.min) / 100.0;
			this.applyValue(this.getter.getAsDouble() + nudge * increment);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float hover = this.updateHover();
		float alpha = this.alphaFactor();

		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		int height = this.getHeight();

		CoreDraw.rowSurface(graphics, x, y, width, height, hover, alpha, this.isLastRow());

		// Name on the left, value right-aligned: the eye can scan either column on its own.
		Component name = this.getMessage();
		Component value = this.formatter.apply(this.getter.getAsDouble());
		int valueWidth = Minecraft.getInstance().font.width(value);
		CoreDraw.clippedText(graphics, Minecraft.getInstance().font, name, x + 10, y + 4,
				Math.max(1, width - 20 - valueWidth - 8), CoreTheme.fade(CoreTheme.TEXT, alpha));
		CoreDraw.clippedText(graphics, Minecraft.getInstance().font, value,
				x + width - 10 - valueWidth, y + 4, valueWidth,
				CoreTheme.fade(CoreTheme.blend(CoreTheme.TEXT_MUTED, CoreTheme.ACCENT_BRIGHT, hover), alpha));

		// The track sits in its own band under the label. Deriving its position from the bottom edge
		// rather than from a fixed offset keeps the two apart at every row height.
		int trackX = x + 10;
		int trackWidth = Math.max(1, width - 20);
		int trackThickness = 4;
		int trackY = y + height - 11;
		CoreDraw.roundedRect(graphics, trackX, trackY, trackWidth, trackThickness, 2,
				CoreTheme.fade(CoreTheme.SCROLL_TRACK, alpha));

		int progress = (int) Math.round(this.normalised() * trackWidth);
		if (progress > 0) {
			CoreDraw.roundedRect(graphics, trackX, trackY, progress, trackThickness, 2,
					CoreTheme.fade(CoreTheme.ACCENT, alpha));
		}

		// A grabbable handle: it grows under the cursor so you can see which slider has the drag.
		int handleX = trackX + progress;
		int handleRadius = this.dragging ? 7 : Math.round(5.0F + hover);
		int centreY = trackY + trackThickness / 2;
		CoreDraw.roundedRect(graphics, handleX - handleRadius, centreY - handleRadius,
				handleRadius * 2, handleRadius * 2, handleRadius,
				CoreTheme.fade(CoreTheme.ACCENT_DEEP, alpha));
		CoreDraw.roundedRect(graphics, handleX - handleRadius + 1, centreY - handleRadius + 1,
				handleRadius * 2 - 2, handleRadius * 2 - 2, handleRadius - 1,
				CoreTheme.fade(CoreTheme.ACCENT_BRIGHT, alpha));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, this.getMessage());
		output.add(NarratedElementType.USAGE, this.formatter.apply(this.getter.getAsDouble()));
	}
}
