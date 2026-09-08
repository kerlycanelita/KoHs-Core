package dev.zymekoh.kohscore.gui.tab;

import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.config.CoreConfig;
import dev.zymekoh.kohscore.gui.CoreDraw;
import dev.zymekoh.kohscore.gui.CoreTheme;
import dev.zymekoh.kohscore.gui.preview.ParticleCatalog;
import dev.zymekoh.kohscore.gui.preview.ParticlePreview;
import dev.zymekoh.kohscore.orchestration.PriorityCompatibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The particle tab: every registered type, searchable, each with a live preview beside its name.
 *
 * <p>Rows are drawn directly rather than built as widgets. With well over a hundred entries, making
 * each one a widget would mean repositioning a hundred objects on every scroll frame; drawing only
 * the dozen rows that are actually visible costs the same no matter how long the list gets.
 *
 * <p>Visible rows borrow previews from a small, preallocated ring. The absolute row index selects
 * the slot, so an entry keeps the same simulation while it moves through the viewport and every
 * thumbnail advances exactly once per frame. The pool only grows when the pane is resized; scrolling
 * and rendering allocate nothing.
 */
public final class ParticleListPane {

	private static final int ROW_HEIGHT = 24;
	private static final int PREVIEW_SIZE = 18;
	private static final int SCROLL_STEP = 30;
	private static final int PREVIEW_OVERSCAN_ROWS = 2;

	private ParticlePreview[] previewPool = {new ParticlePreview()};

	private List<ParticleCatalog.Entry> filtered = List.of();
	private String query = "";
	private double scroll;

	private int x;
	private int y;
	private int width;
	private int height;

	private @Nullable EditBox searchBox;
	private ParticleCatalog.@Nullable Entry hovered;
	private PriorityCompatibility.@Nullable Reservation hoveredReservation;
	private boolean locked;

	/** Builds the search box. The caller adds it to the screen so it receives input. */
	public EditBox createSearchBox(Font font, int boxX, int boxY, int boxWidth) {
		EditBox box = new EditBox(font, boxX, boxY, boxWidth, 16,
				Component.translatable("kohs_core.particles.search"));
		box.setBordered(false);
		box.setTextColor(CoreTheme.TEXT);
		box.setHint(Component.translatable("kohs_core.particles.search.hint"));
		box.setMaxLength(64);
		box.setResponder(value -> {
			this.query = value.toLowerCase(Locale.ROOT).trim();
			this.scroll = 0.0;
			this.refilter();
		});
		this.searchBox = box;
		this.refilter();
		return box;
	}

	/** Positions the scrolling area. */
	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.ensurePreviewCapacity(height);
		this.clampScroll();
	}

	/** Grows outside the render loop to one simulation per visible/overscan row. */
	private void ensurePreviewCapacity(int visibleHeight) {
		int required = Math.max(1, visibleHeight / ROW_HEIGHT + PREVIEW_OVERSCAN_ROWS);
		if (required <= this.previewPool.length) {
			return;
		}

		ParticlePreview[] enlarged = new ParticlePreview[required];
		System.arraycopy(this.previewPool, 0, enlarged, 0, this.previewPool.length);
		for (int index = this.previewPool.length; index < enlarged.length; index++) {
			enlarged[index] = new ParticlePreview();
		}
		this.previewPool = enlarged;
	}

	/** Greys the whole list out when another mod owns the particle domain. */
	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	private void refilter() {
		List<ParticleCatalog.Entry> all = ParticleCatalog.all();
		if (this.query.isEmpty()) {
			this.filtered = all;
			return;
		}
		List<ParticleCatalog.Entry> matches = new ArrayList<>();
		for (ParticleCatalog.Entry entry : all) {
			if (entry.searchBlob().contains(this.query)) {
				matches.add(entry);
			}
		}
		this.filtered = List.copyOf(matches);
	}

	/** Ensures the list is populated, e.g. after a resource reload. */
	public void ensureLoaded() {
		if (this.filtered.isEmpty() && this.query.isEmpty()) {
			this.refilter();
		}
	}

	private int contentHeight() {
		return this.filtered.size() * ROW_HEIGHT;
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, this.contentHeight() - this.height);
		this.scroll = Mth.clamp(this.scroll, 0.0, maxScroll);
	}

	/** How many types are currently listed, for the header count. */
	public int visibleCount() {
		return this.filtered.size();
	}

	/** The row under the cursor, or null. Used to feed the description panel. */
	public ParticleCatalog.@Nullable Entry hoveredEntry() {
		return this.hovered;
	}

	/** Exact priority reservation on the hovered row, if one exists. */
	public PriorityCompatibility.@Nullable Reservation hoveredReservation() {
		return this.hoveredReservation;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (!this.isInside(mouseX, mouseY)) {
			return false;
		}
		this.scroll -= verticalAmount * SCROLL_STEP;
		this.clampScroll();
		return true;
	}

	/** Toggles the clicked row. Returns true when the click was consumed. */
	public boolean mouseClicked(double mouseX, double mouseY) {
		if (this.locked || !this.isInside(mouseX, mouseY)) {
			return false;
		}
		int index = (int) ((mouseY - this.y + this.scroll) / ROW_HEIGHT);
		if (index < 0 || index >= this.filtered.size()) {
			return false;
		}
		ParticleCatalog.Entry entry = this.filtered.get(index);
		if (PriorityCompatibility.particleReservation(entry.id()) != null) {
			return true;
		}
		CoreConfig config = ConfigStore.get();
		boolean enabled = !Boolean.FALSE.equals(config.particleOverrides.get(entry.id()));
		if (enabled) {
			config.particleOverrides.put(entry.id(), Boolean.FALSE);
		} else {
			// Removing rather than storing true keeps the config file free of redundant entries.
			config.particleOverrides.remove(entry.id());
		}
		ConfigStore.save();
		return true;
	}

	/** Turns every listed type on or off at once, respecting the current search filter. */
	public void setAllVisible(boolean enabled) {
		if (this.locked) {
			return;
		}
		CoreConfig config = ConfigStore.get();
		for (ParticleCatalog.Entry entry : this.filtered) {
			if (PriorityCompatibility.particleReservation(entry.id()) != null) {
				continue;
			}
			if (enabled) {
				config.particleOverrides.remove(entry.id());
			} else {
				config.particleOverrides.put(entry.id(), Boolean.FALSE);
			}
		}
		ConfigStore.save();
	}

	/**
	 * Particles that exist only to decorate a place.
	 *
	 * <p>Chosen on one rule: does switching it off hide anything a player could act on? Smoke from a
	 * campfire, drips in a cave and falling leaves are scenery. Explosions, crits and effect clouds
	 * are not, and none of them are here — turning those off would change what you can see happening
	 * to you, which is a decision for the player to make row by row, not something a preset should
	 * do quietly.
	 */
	private static final List<String> AMBIENT_KEYWORDS = List.of(
			"ash", "spore", "pollen", "mycelium", "dripping", "falling", "landing", "campfire",
			"cherry_leaves", "pale_oak_leaves", "tinted_leaves", "firefly", "warped_spore",
			"crimson_spore", "underwater", "bubble_column", "fog", "note", "cloud", "smoke");

	/**
	 * Turns the decorative particles off and leaves everything else exactly as it was.
	 *
	 * <p>Additive on purpose: it never re-enables a type the player switched off by hand.
	 */
	public void applyAmbientPreset() {
		if (this.locked) {
			return;
		}
		CoreConfig config = ConfigStore.get();
		for (ParticleCatalog.Entry entry : ParticleCatalog.all()) {
			if (PriorityCompatibility.particleReservation(entry.id()) != null) {
				continue;
			}
			String path = entry.id().substring(entry.id().indexOf(':') + 1);
			for (String keyword : AMBIENT_KEYWORDS) {
				if (path.contains(keyword)) {
					config.particleOverrides.put(entry.id(), Boolean.FALSE);
					break;
				}
			}
		}
		ConfigStore.save();
	}

	private boolean isInside(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y
				&& mouseY < this.y + this.height;
	}

	/** Draws the visible rows. */
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		this.ensureLoaded();
		this.hovered = null;
		this.hoveredReservation = null;

		Font font = Minecraft.getInstance().font;
		CoreConfig config = ConfigStore.get();
		float alpha = this.locked ? 0.4F : 1.0F;
		boolean globallyOff = config.particlesDisabledGlobally;

		CoreDraw.roundedRect(graphics, this.x - 4, this.y - 4, this.width + 8, this.height + 8, 5,
				CoreTheme.fade(CoreTheme.PANEL_DEEP, alpha));

		graphics.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);

		int first = Math.max(0, (int) (this.scroll / ROW_HEIGHT));
		int last = Math.min(this.filtered.size(), first + this.height / ROW_HEIGHT + 2);

		for (int index = first; index < last; index++) {
			ParticleCatalog.Entry entry = this.filtered.get(index);
			PriorityCompatibility.Reservation reservation =
					PriorityCompatibility.particleReservation(entry.id());
			boolean rowLocked = this.locked || reservation != null;
			int rowY = this.y + index * ROW_HEIGHT - (int) Math.round(this.scroll);
			boolean rowHovered = mouseX >= this.x && mouseX < this.x + this.width
					&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && this.isInside(mouseX, mouseY);
			if (rowHovered) {
				this.hovered = entry;
				this.hoveredReservation = reservation;
			}

			boolean enabled = reservation != null
					|| (!globallyOff && !Boolean.FALSE.equals(config.particleOverrides.get(entry.id())));
			float rowAlpha = rowLocked ? alpha * 0.48F : alpha;

			if (rowHovered) {
				CoreDraw.roundedRect(graphics, this.x + 1, rowY + 1, this.width - 2, ROW_HEIGHT - 2, 4,
						CoreTheme.fade(CoreTheme.PANEL_HOVER, rowAlpha));
			}

			// The preview sits to the left of the name, and keeps animating whether the type is on
			// or off, so the player can see what they are about to switch back on.
			int previewX = this.x + 4;
			int previewY = rowY + (ROW_HEIGHT - PREVIEW_SIZE) / 2;
			// Modulo keeps the binding stable when the first visible row changes. The pool is
			// larger than the visible range, so two rows drawn this frame never share a slot.
			ParticlePreview preview = this.previewPool[index % this.previewPool.length];
			preview.configure(entry.archetype(), PREVIEW_SIZE, PREVIEW_SIZE, entry.id().hashCode());
			preview.renderFramed(graphics, previewX, previewY, rowHovered);

			int textX = previewX + PREVIEW_SIZE + 8;
			int textY = rowY + (ROW_HEIGHT - 8) / 2;
			int nameColor = enabled ? CoreTheme.TEXT : CoreTheme.TEXT_DISABLED;
			int stateWidth = 34;
			CoreDraw.clippedText(graphics, font, entry.displayName(), textX, textY,
					this.width - (textX - this.x) - stateWidth - 10, CoreTheme.fade(nameColor, rowAlpha));

			// A dot rather than a word: at this row height a label would crowd the name.
			int dotX = this.x + this.width - 14;
			int dotY = rowY + ROW_HEIGHT / 2 - 3;
			int dotColor = enabled ? CoreTheme.GOOD : CoreTheme.BAD;
			CoreDraw.roundedRect(graphics, dotX, dotY, 6, 6, 3, CoreTheme.fade(dotColor, rowAlpha));
		}

		graphics.disableScissor();

		CoreDraw.scrollFade(graphics, this.x, this.y, this.width, this.height, this.scroll > 1.0,
				this.scroll < this.contentHeight() - this.height - 1);
		CoreDraw.scrollbar(graphics, this.x + this.width + 1, this.y, this.height, this.contentHeight(),
				this.height, this.scroll);

		if (this.filtered.isEmpty()) {
			// Drawn after the scissor is released, so this has to bound itself: centre it when it
			// fits and left-clip it when the pane is too narrow, rather than bleeding into the rail.
			Component message = Component.translatable("kohs_core.particles.no_results");
			int messageY = this.y + this.height / 2 - 4;
			if (font.width(message) <= this.width - 8) {
				graphics.centeredText(font, message, this.x + this.width / 2, messageY,
						CoreTheme.fade(CoreTheme.TEXT_DIM, alpha));
			} else {
				CoreDraw.clippedText(graphics, font, message, this.x + 4, messageY,
						Math.max(1, this.width - 8), CoreTheme.fade(CoreTheme.TEXT_DIM, alpha));
			}
		}
	}
}
