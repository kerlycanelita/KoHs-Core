package dev.zymekoh.kohscore.gui.widget;

import dev.zymekoh.kohscore.gui.CoreTheme;
import java.time.Duration;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Shared behaviour for Core's controls: hover animation, and the locked state.
 *
 * <p>The lock is the visible half of the domain registry. When another mod owns a setting, the
 * control does not disappear — it stays exactly where it was, dimmed and unclickable, with a tooltip
 * naming the mod that took it. Hiding it would leave the player wondering where the option went;
 * leaving it live would mean two mods writing the same value every frame.
 */
public abstract class CoreControl extends AbstractWidget {

	/** Alpha multiplier applied to everything when the control is locked. */
	protected static final float LOCKED_FADE = 0.38F;

	private @Nullable Component lockOwner;
	private Component description = Component.empty();

	private float hoverAmount;
	private float revealAlpha = 1.0F;

	public void setRevealAlpha(float alpha) {
		this.revealAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
	}
	private long lastFrameMillis = System.nanoTime() / 1_000_000L;

	protected CoreControl(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	/**
	 * Marks this control as owned by another mod.
	 *
	 * @param ownerName display name of the owning mod, or {@code null} to unlock
	 * @param reason    sentence for the tooltip explaining what the owner is doing
	 */
	public void lockTo(@Nullable Component ownerName, @Nullable Component reason) {
		this.lockOwner = ownerName;
		if (ownerName == null) {
			this.active = true;
			this.setTooltip(null);
		} else {
			this.active = false;
			Component tooltip = reason == null
					? Component.translatable("kohs_core.locked.short", ownerName)
					: Component.translatable("kohs_core.locked.long", ownerName).append("\n").append(reason);
			this.setTooltip(Tooltip.create(tooltip));
			this.setTooltipDelay(Duration.ofMillis(220));
		}
	}

	private boolean lastRow;

	/**
	 * Marks this as the final row of its group, so it draws no trailing separator.
	 *
	 * <p>A separator under the last row reads as the start of something that is not there.
	 */
	public void setLastRow(boolean lastRow) {
		this.lastRow = lastRow;
	}

	public boolean isLastRow() {
		return this.lastRow;
	}

	/** Whether another mod currently owns this setting. */
	public boolean isLocked() {
		return this.lockOwner != null;
	}

	public @Nullable Component lockOwner() {
		return this.lockOwner;
	}

	/** The sentence the description panel types out while this control is hovered. */
	public Component description() {
		return this.description;
	}

	public void setDescription(Component description) {
		this.description = description;
	}

	/**
	 * Advances the hover animation and returns its 0..1 position.
	 *
	 * <p>Time based rather than frame based, so the fade takes the same real duration at any frame
	 * rate.
	 */
	protected float updateHover() {
		long now = System.nanoTime() / 1_000_000L;
		float elapsed = Math.min(50L, Math.max(0L, now - this.lastFrameMillis)) / 1000.0F;
		this.lastFrameMillis = now;

		float target = this.active && this.isHoveredOrFocused() ? 1.0F : 0.0F;
		this.hoverAmount += (target - this.hoverAmount) * CoreTheme.approach(elapsed, 14.0F);
		return this.hoverAmount;
	}

	/** The alpha factor to apply to every colour this frame. */
	protected float alphaFactor() {
		return (this.isLocked() ? LOCKED_FADE : 1.0F) * this.revealAlpha;
	}
}
