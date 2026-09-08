package dev.zymekoh.kohscore.api;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A tab a sibling mod contributes to Core's settings screen.
 *
 * <p>Implement either path: return a {@link Screen} from {@link #createScreen} to take over the
 * whole pane, or leave that null and return {@link CoreOption}s so Core renders the controls in its
 * own style.
 */
public interface CoreSettingsPage {

	/** Stable id, unique per mod. Used for persistence and for deep links. */
	String id();

	/** Tab label. */
	Component title();

	/** Sentence typed out in the description panel when the tab is hovered. */
	Component description();

	/**
	 * Accent colour for the tab, as 0xAARRGGBB. Return 0 to inherit Core's purple.
	 */
	default int accentColor() {
		return 0;
	}

	/**
	 * A screen to open instead of rendering options inline, or {@code null} to use {@link #options}.
	 *
	 * @param parent the screen to return to on close
	 */
	default @Nullable Screen createScreen(Screen parent) {
		return null;
	}

	/** Controls for Core to render, used when {@link #createScreen} returns null. */
	default List<CoreOption> options() {
		return List.of();
	}
}
