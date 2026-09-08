package dev.zymekoh.kohscore.mixin;

import dev.zymekoh.kohscore.config.ConfigStore;
import dev.zymekoh.kohscore.gui.CoreScreen;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Replaces only the screen supplier attached to vanilla's Video Settings button.
 *
 * <p>The button itself remains in vanilla's layout, so resizing, keyboard navigation and narration
 * keep working exactly as they do without Core. The field-bounded slice avoids relying on a global
 * invocation ordinal and leaves every neighboring options button untouched.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {

	@ModifyArg(
			method = "init()V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/options/OptionsScreen;openScreenButton(Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)Lnet/minecraft/client/gui/components/Button;"
			),
			slice = @Slice(
					from = @At(
							value = "FIELD",
							target = "Lnet/minecraft/client/gui/screens/options/OptionsScreen;VIDEO:Lnet/minecraft/network/chat/Component;"
					),
					to = @At(
							value = "FIELD",
							target = "Lnet/minecraft/client/gui/screens/options/OptionsScreen;CONTROLS:Lnet/minecraft/network/chat/Component;"
					)
			),
			index = 1
	)
	private Supplier<Screen> kohsCore$replaceVideoSettingsSupplier(Supplier<Screen> vanillaSupplier) {
		if (!ConfigStore.get().takeOverVideoSettings) {
			return vanillaSupplier;
		}

		Screen parent = (Screen) (Object) this;
		return () -> new CoreScreen(parent);
	}
}
