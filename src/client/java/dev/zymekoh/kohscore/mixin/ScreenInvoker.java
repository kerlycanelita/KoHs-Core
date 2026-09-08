package dev.zymekoh.kohscore.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens up {@link Screen}'s protected widget methods.
 *
 * <p>Core swaps vanilla's Video Settings button for its own from a Fabric screen event rather than
 * by injecting into {@code OptionsScreen}. Doing it that way needs to add and remove widgets from
 * outside the screen, hence these two.
 *
 * <p>An invoker adds a synthetic accessor; it does not inject into any existing method body, so it
 * cannot collide with another mod touching the same class.
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T kohsCore$addRenderableWidget(T widget);

	@Invoker("removeWidget")
	void kohsCore$removeWidget(GuiEventListener widget);
}
