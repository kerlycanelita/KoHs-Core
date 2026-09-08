package dev.zymekoh.kohscore.orchestration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.zymekoh.kohscore.gui.CoreScreen;

/**
 * Opens Core's screen from Mod Menu.
 *
 * <p>Mod Menu is a {@code compileOnly} dependency, so this class is only ever loaded when Mod Menu
 * is actually installed; the entrypoint simply goes unused otherwise.
 */
public final class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CoreScreen::new;
	}
}
