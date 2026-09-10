package dev.zymekoh.kohscore.profile;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Rendering goals, not promises of a particular FPS or end-to-end input latency. */
public enum GameProfile {
	LATENCY, FPS, QUALITY;
	public String key() { return "kohs_core.profile." + name().toLowerCase(Locale.ROOT); }
	public Component title() { return Component.translatable(key()); }
	public Component description() { return Component.translatable(key() + ".description"); }
}
