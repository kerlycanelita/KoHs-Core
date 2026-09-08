package dev.zymekoh.kohscore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.zymekoh.kohscore.KoHsCore;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads and saves {@link CoreConfig}.
 *
 * <p>Two things matter here. Writes are atomic, so a crash mid-save cannot leave a truncated file
 * that wipes the player's settings on next launch. And they happen on a daemon thread, because the
 * settings screen saves on every toggle and a synchronous write would show up as a hitch on the
 * frame the player clicked.
 */
public final class ConfigStore {

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final Path CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("kohs_core.json");

	private static final ScheduledThreadPoolExecutor SAVE_EXECUTOR = createSaveExecutor();

	private static ScheduledThreadPoolExecutor createSaveExecutor() {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
		Thread thread = new Thread(runnable, "KoHs Core config writer");
		thread.setDaemon(true);
		return thread;
		});
		// Cancelled slider steps leave the queue immediately instead of retaining hundreds of
		// ScheduledFuture instances until their original delay expires.
		executor.setRemoveOnCancelPolicy(true);
		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		return executor;
	}

	private static volatile CoreConfig current = new CoreConfig();
	private static final Object SAVE_LOCK = new Object();
	private static final Object WRITE_LOCK = new Object();
	private static @org.jspecify.annotations.Nullable ScheduledFuture<?> pendingSave;
	private static long saveRevision;

	private ConfigStore() {
	}

	/** The live config. Never null; a failed load leaves defaults in place. */
	public static CoreConfig get() {
		return current;
	}

	/** Reads the file from disk, falling back to defaults on anything unreadable. */
	public static void load() {
		CoreConfig loaded = new CoreConfig();
		if (Files.isRegularFile(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
				CoreConfig parsed = GSON.fromJson(reader, CoreConfig.class);
				if (parsed != null) {
					loaded = parsed;
				}
			} catch (IOException | JsonSyntaxException failure) {
				// A corrupt config is not worth crashing over, but the player should know why their
				// settings reset, so this is a warning rather than a debug line.
				KoHsCore.LOGGER.warn("Could not read {}, falling back to defaults", CONFIG_PATH, failure);
			}
		}
		loaded.sanitize();
		current = loaded;
	}

	/** Replaces the live config and persists it. */
	public static void replace(CoreConfig replacement) {
		replacement.sanitize();
		current = replacement;
		save();
	}

	/**
	 * Debounces an asynchronous write of the current config.
	 *
	 * <p>A slider can cross dozens of steps in one drag. Writing every intermediate value creates an
	 * avoidable I/O burst that may contend with chunk loading, so only the final value after 250 ms
	 * of quiet reaches disk.
	 */
	public static void save() {
		synchronized (SAVE_LOCK) {
			long revision = ++saveRevision;
			if (pendingSave != null) {
				pendingSave.cancel(false);
			}
			pendingSave = SAVE_EXECUTOR.schedule(() -> {
				CoreConfig snapshot;
				synchronized (SAVE_LOCK) {
					if (revision != saveRevision) {
						return;
					}
					// Snapshot only the settled value. The former eager copy cloned the complete
					// particle override map for every single intermediate slider position.
					snapshot = current.copy();
				}
				synchronized (WRITE_LOCK) {
					synchronized (SAVE_LOCK) {
						if (revision != saveRevision) {
							return;
						}
					}
					writeNow(snapshot);
				}
				synchronized (SAVE_LOCK) {
					if (revision == saveRevision) {
						pendingSave = null;
					}
				}
			}, 250L, TimeUnit.MILLISECONDS);
		}
	}

	/** Persists the final live state before Minecraft shuts down. */
	public static void flush() {
		CoreConfig snapshot = current.copy();
		synchronized (SAVE_LOCK) {
			saveRevision++;
			if (pendingSave != null) {
				pendingSave.cancel(false);
				pendingSave = null;
			}
		}
		// Serialising against the background writer prevents an older snapshot from winning a
		// shutdown race and replacing the player's last changes.
		synchronized (WRITE_LOCK) {
			writeNow(snapshot);
		}
	}

	private static void writeNow(CoreConfig config) {
		Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
			try {
				Files.move(temporary, CONFIG_PATH, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException unsupported) {
				// Some network and virtualised filesystems refuse atomic moves. A plain replace is
				// still better than writing the destination in place.
				Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException failure) {
			KoHsCore.LOGGER.error("Could not write {}", CONFIG_PATH, failure);
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
				// Nothing useful left to do; the stale temp file is harmless.
			}
		}
	}
}
