package dev.zymekoh.kohscore.orchestration;

import dev.zymekoh.kohscore.KoHsCore;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Discovers installed suite members without changing their files or initialising absent mods. */
public final class SuiteCatalog {
    public record Entry(String id, String name, Component description, boolean installed,
            String version, Function<Screen, Screen> factory) {
        public boolean configurable() { return installed && factory != null; }
        public Component status() {
            return Component.translatable(!installed ? "kohs_core.suite.missing"
                    : factory == null ? "kohs_core.suite.no_menu" : "kohs_core.suite.configure");
        }
        public boolean open(Screen parent) {
            if (!configurable()) return false;
            try {
                Screen screen = factory.apply(parent);
                if (screen != null) {
                    Minecraft.getInstance().setScreen(screen);
                    return true;
                }
            } catch (RuntimeException | LinkageError failure) {
                KoHsCore.LOGGER.warn("Could not open the configuration of {}", id, failure);
            }
            return false;
        }
    }

    private record Known(String id, String name, String screenClass) {}
    private static final List<Known> KNOWN = List.of(
            new Known("crystal_tweaks", "Crystal Tweaks", "com.zymekoh.crystaltweaks.client.CrystalTweaksScreen"),
            new Known("kohs_anchors", "KoHs Anchors", ""),
            new Known("herzium", "Herzium", ""),
            new Known("kohs_inventory_tweaks", "Inventory Tweaks", "dev.zymekoh.kohsinventorytweaks.screen.InventoryTweaksScreen"),
            new Known("kohs_hands_position", "KoHs Hand Position", "dev.zymekoh.handposition.gui.HandsConfigScreen"));

    private SuiteCatalog() {}

    public static List<Entry> discover() {
        FabricLoader loader = FabricLoader.getInstance();
        Map<String, Function<Screen, Screen>> menus = new LinkedHashMap<>();
        if (loader.isModLoaded("modmenu")) SuiteModMenuBridge.discover(menus);
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Known known : KNOWN) {
            var mod = loader.getModContainer(known.id());
            Function<Screen, Screen> factory = menus.get(known.id());
            if (mod.isPresent() && factory == null) factory = directFactory(known);
            entries.put(known.id(), new Entry(known.id(), known.name(),
                    Component.translatable("kohs_core.suite.description." + known.id()), mod.isPresent(),
                    mod.map(value -> value.getMetadata().getVersion().getFriendlyString()).orElse(""), factory));
        }
        List<net.fabricmc.loader.api.ModContainer> extra = new ArrayList<>(loader.getAllMods());
        extra.sort(Comparator.comparing(value -> value.getMetadata().getName()));
        for (var mod : extra) {
            var metadata = mod.getMetadata();
            if (entries.containsKey(metadata.getId()) || metadata.getId().equals("kohs_core")) continue;
            boolean author = metadata.getAuthors().stream().anyMatch(person -> {
                String name = person.getName().strip().toLowerCase(Locale.ROOT);
                return name.equals("zymekoh") || name.equals("koh");
            });
            if (!author) continue;
            entries.put(metadata.getId(), new Entry(metadata.getId(), metadata.getName(),
                    Component.literal(metadata.getDescription()), true,
                    metadata.getVersion().getFriendlyString(), menus.get(metadata.getId())));
        }
        return List.copyOf(entries.values());
    }

    private static Function<Screen, Screen> directFactory(Known known) {
        if (known.screenClass().isEmpty()) return null;
        try {
            Class<?> type = Class.forName(known.screenClass(), false, SuiteCatalog.class.getClassLoader());
            if (!Screen.class.isAssignableFrom(type)) return null;
            Constructor<?> constructor = type.getConstructor(Screen.class);
            return parent -> {
                try {
                    if (known.id().equals("kohs_inventory_tweaks") && Minecraft.getInstance().level == null) {
                        Class<?> unavailable = Class.forName("dev.zymekoh.kohsinventorytweaks.screen.ConfigurationUnavailableScreen");
                        return (Screen) unavailable.getConstructor(Screen.class).newInstance(parent);
                    }
                    return (Screen) constructor.newInstance(parent);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Configuration screen unavailable for " + known.id(), failure);
                }
            };
        } catch (ReflectiveOperationException | LinkageError failure) {
            return null;
        }
    }
}
