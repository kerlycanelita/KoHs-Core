package dev.zymekoh.kohscore.api;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;

/**
 * A single control a sibling mod wants Core to draw on its behalf.
 *
 * <p>Handing Core an option instead of a whole screen means the mod inherits Core's look, its hover
 * descriptions and its ownership greying for free. A mod that would rather draw its own thing can
 * ignore this entirely and return a screen from {@link CoreSettingsPage#createScreen}.
 */
public sealed interface CoreOption {

	/** Label shown next to the control. */
	Component title();

	/** Sentence shown in the description panel while the control is hovered. */
	Component description();

	/** An on/off switch. */
	record Toggle(Component title, Component description, BooleanSupplier getter, Consumer<Boolean> setter)
			implements CoreOption {
	}

	/**
	 * A numeric slider.
	 *
	 * @param formatter renders the current value for the slider label
	 */
	record Slider(Component title, Component description, double min, double max, double step,
			DoubleSupplier getter, Consumer<Double> setter, java.util.function.DoubleFunction<Component> formatter)
			implements CoreOption {
	}

	/**
	 * A cycling choice between named values.
	 *
	 * @param labels one label per choice, indexed by the getter's return value
	 */
	record Choice(Component title, Component description, List<Component> labels, IntSupplier getter,
			Consumer<Integer> setter) implements CoreOption {
	}

	/** A button that just runs something. */
	record Action(Component title, Component description, Runnable onPress) implements CoreOption {
	}
}
