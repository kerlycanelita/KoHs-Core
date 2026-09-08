package dev.zymekoh.kohscore.jvm;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zymekoh.kohscore.KoHsCore;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * What this machine actually is.
 *
 * <p>The JVM advice is only worth reading if the numbers behind it are real, so nothing here is
 * assumed: RAM, cores and GPU come from OSHI, which Minecraft already ships for crash reports, and
 * the JVM half comes from the management beans.
 *
 * <p>OSHI on Windows goes through WMI and can take a noticeable moment, so the probe runs once on a
 * background thread at startup and the result is cached for the session. Every field has a safe
 * fallback, because a probe failing is not a reason to lose the whole settings screen.
 */
public record HardwareProbe(
		String cpuName,
		String cpuVendor,
		String cpuMicroarchitecture,
		int physicalCores,
		int logicalCores,
		double cpuBaseFrequencyGhz,
		long totalMemoryBytes,
		long availableMemoryBytes,
		long swapTotalBytes,
		String gpuName,
		String gpuVendor,
		long gpuVramBytes,
		String glRenderer,
		String glBackend,
		int refreshRateHz,
		String javaVersion,
		String javaVendor,
		boolean sixtyFourBit,
		long currentMaxHeapBytes,
		String currentCollector,
		List<String> currentJvmFlags) {

	private static final long GIB = 1024L * 1024L * 1024L;

	private static volatile HardwareProbe cached;
	private static volatile boolean probing;

	/** Kicks the probe off in the background. Safe to call more than once. */
	public static void beginProbe() {
		if (cached != null || probing) {
			return;
		}
		probing = true;
		CompletableFuture.supplyAsync(HardwareProbe::measure).thenAccept(result -> {
			cached = result;
			probing = false;
			KoHsCore.LOGGER.info("Hardware probe: {} ({}c/{}t), {} GiB RAM, {}",
					result.cpuName(), result.physicalCores(), result.logicalCores(),
					result.totalMemoryBytes() / GIB, result.gpuName());
		}).exceptionally(failure -> {
			KoHsCore.LOGGER.warn("Hardware probe failed; falling back to JVM-only figures", failure);
			cached = fallback();
			probing = false;
			return null;
		});
	}

	/** The cached probe, or a JVM-only fallback while the real one is still running. */
	public static HardwareProbe get() {
		HardwareProbe result = cached;
		if (result == null) {
			beginProbe();
			return fallback();
		}
		return result;
	}

	/** Whether the full hardware probe has landed, as opposed to the fallback. */
	public static boolean isReady() {
		return cached != null;
	}

	private static HardwareProbe measure() {
		String cpuName = "Unknown CPU";
		String cpuVendor = "";
		String microarchitecture = "";
		int physicalCores = Runtime.getRuntime().availableProcessors();
		int logicalCores = physicalCores;
		double baseGhz = 0.0;
		long totalMemory = 0L;
		long availableMemory = 0L;
		long swapTotal = 0L;
		String gpuName = "Unknown GPU";
		String gpuVendor = "";
		long vram = 0L;

		try {
			HardwareAbstractionLayer hardware = new SystemInfo().getHardware();

			CentralProcessor processor = hardware.getProcessor();
			CentralProcessor.ProcessorIdentifier identifier = processor.getProcessorIdentifier();
			cpuName = identifier.getName().trim();
			cpuVendor = identifier.getVendor().trim();
			microarchitecture = identifier.getMicroarchitecture();
			physicalCores = processor.getPhysicalProcessorCount();
			logicalCores = processor.getLogicalProcessorCount();
			baseGhz = identifier.getVendorFreq() / 1.0E9;

			GlobalMemory memory = hardware.getMemory();
			totalMemory = memory.getTotal();
			availableMemory = memory.getAvailable();
			swapTotal = memory.getVirtualMemory().getSwapTotal();

			// A laptop reports both the integrated and the discrete GPU. The one with the most
			// VRAM is the one the game is realistically running on.
			GraphicsCard best = null;
			for (GraphicsCard card : hardware.getGraphicsCards()) {
				if (best == null || card.getVRam() > best.getVRam()) {
					best = card;
				}
			}
			if (best != null) {
				gpuName = best.getName().trim();
				gpuVendor = best.getVendor().trim();
				vram = best.getVRam();
			}
		} catch (Throwable failure) {
			// OSHI reaches into native libraries, so this catches Throwable rather than Exception.
			KoHsCore.LOGGER.warn("OSHI probe incomplete; using what was gathered", failure);
		}

		return new HardwareProbe(cpuName, cpuVendor, microarchitecture, physicalCores, logicalCores, baseGhz,
				totalMemory, availableMemory, swapTotal, gpuName, gpuVendor, vram,
				probeGlRenderer(), probeGlBackend(), refreshRate(),
				System.getProperty("java.version", "?"), System.getProperty("java.vendor", "?"),
				isSixtyFourBit(), Runtime.getRuntime().maxMemory(), collectorName(), jvmFlags());
	}

	private static HardwareProbe fallback() {
		int cores = Runtime.getRuntime().availableProcessors();
		return new HardwareProbe("Unknown CPU", "", "", cores, cores, 0.0,
				0L, 0L, 0L, "Unknown GPU", "", 0L, probeGlRenderer(), probeGlBackend(), refreshRate(),
				System.getProperty("java.version", "?"), System.getProperty("java.vendor", "?"),
				isSixtyFourBit(), Runtime.getRuntime().maxMemory(), collectorName(), jvmFlags());
	}

	private static String probeGlRenderer() {
		try {
			var device = RenderSystem.tryGetDevice();
			return device == null ? "" : device.getRenderer();
		} catch (Throwable ignored) {
			// tryGetDevice is only valid on the render thread; off it, this is simply unknown.
			return "";
		}
	}

	private static String probeGlBackend() {
		try {
			var device = RenderSystem.tryGetDevice();
			return device == null ? "" : device.getBackendName();
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static int refreshRate() {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft == null || minecraft.getWindow() == null ? 0 : minecraft.getWindow().getRefreshRate();
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static boolean isSixtyFourBit() {
		String dataModel = System.getProperty("sun.arch.data.model", "64");
		return !"32".equals(dataModel);
	}

	private static String collectorName() {
		List<String> names = new ArrayList<>();
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			names.add(bean.getName());
		}
		String joined = String.join(", ", names);
		// The bean names are implementation detail; map them to what a player would recognise.
		if (joined.contains("ZGC") || joined.contains("Z Concurrent")) {
			return "ZGC";
		}
		if (joined.contains("G1")) {
			return "G1";
		}
		if (joined.contains("Shenandoah")) {
			return "Shenandoah";
		}
		if (joined.contains("PS ") || joined.contains("Parallel")) {
			return "Parallel";
		}
		if (joined.contains("Serial") || joined.contains("Copy")) {
			return "Serial";
		}
		return joined.isEmpty() ? "Unknown" : joined;
	}

	private static List<String> jvmFlags() {
		try {
			return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
					.filter(argument -> argument.startsWith("-X") || argument.startsWith("-D"))
					.collect(Collectors.toList());
		} catch (Throwable ignored) {
			return List.of();
		}
	}

	/** Total physical RAM in GiB, or 0 when it could not be read. */
	public double totalMemoryGib() {
		return this.totalMemoryBytes / (double) GIB;
	}

	/** Currently free physical RAM in GiB. */
	public double availableMemoryGib() {
		return this.availableMemoryBytes / (double) GIB;
	}

	/** The heap ceiling this launch was given, in GiB. */
	public double currentMaxHeapGib() {
		return this.currentMaxHeapBytes / (double) GIB;
	}

	/** VRAM in GiB, or 0 when unknown. */
	public double gpuVramGib() {
		return this.gpuVramBytes / (double) GIB;
	}

	/** The major Java version, e.g. 25. Returns 0 when unparseable. */
	public int javaMajorVersion() {
		try {
			String version = this.javaVersion;
			int dot = version.indexOf('.');
			int dash = version.indexOf('-');
			int end = version.length();
			if (dot > 0) {
				end = Math.min(end, dot);
			}
			if (dash > 0) {
				end = Math.min(end, dash);
			}
			return Integer.parseInt(version.substring(0, end));
		} catch (RuntimeException ignored) {
			return 0;
		}
	}
}
