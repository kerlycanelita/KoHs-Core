package dev.zymekoh.kohscore.orchestration;

import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.zymekoh.kohscore.KoHsCore;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/** Loaded only when Mod Menu is installed, keeping that dependency optional. */
final class SuiteModMenuBridge {
    static void discover(Map<String, Function<Screen, Screen>> menus) {
        for (var container : FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
            String id = container.getProvider().getMetadata().getId();
            try {
                var factory = container.getEntrypoint().getModConfigScreenFactory();
                if (factory != null) menus.put(id, parent -> factory.create(parent));
            } catch (RuntimeException | LinkageError failure) {
                KoHsCore.LOGGER.warn("Suite could not query {} configuration factory", id, failure);
            }
        }
    }
}
