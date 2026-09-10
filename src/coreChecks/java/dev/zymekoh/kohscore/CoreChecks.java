package dev.zymekoh.kohscore;

import dev.zymekoh.kohscore.optimize.FrameWindow;
import dev.zymekoh.kohscore.profile.ProfileChanges;

/** Dependency-free regression checks; run with Gradle coreChecks (also included in check/build). */
public final class CoreChecks {
	private static int assertions;
	private static final class Setting { int value; boolean owned = true; Setting(int value) { this.value = value; } }
	public static void main(String[] args) {
		var first = new Setting(4);
		var second = new Setting(12);
		var locked = new Setting(8); locked.owned = false;
		var changes = new ProfileChanges();
		add(changes, first, 2); add(changes, second, 6); add(changes, locked, 2);
		check(changes.apply().equals(new ProfileChanges.Result(2, 1)), "apply counts");
		check(first.value == 2 && second.value == 6 && locked.value == 8, "reserved setting unchanged");
		first.value = 5;
		second.owned = false;
		check(changes.undo().equals(new ProfileChanges.Result(0, 2)), "undo respects later edits and owners");
		check(first.value == 5 && second.value == 6, "undo does not clobber");
		check(!changes.canUndo(), "undo consumed");

		var reversible = new ProfileChanges();
		add(reversible, first, 2);
		reversible.apply();
		check(reversible.undo().changed() == 1 && first.value == 5, "exact restore");
		var noop = new ProfileChanges(); add(noop, first, 5);
		check(noop.apply().changed() == 0 && !noop.canUndo(), "no-op avoids write and undo");

		var clamped = new ProfileChanges();
		clamped.add(() -> true, () -> first.value, v -> first.value = Math.min(10, v), 99);
		clamped.apply();
		check(first.value == 10, "setter validation respected");
		check(clamped.undo().changed() == 1 && first.value == 5, "undo uses actual validated value");

		var failed = new ProfileChanges(); add(failed, first, 2);
		failed.add(() -> true, () -> second.value, v -> { second.value = v; if (v == 1) throw new IllegalStateException("test"); }, 1);
		try { failed.apply(); throw new AssertionError("failure not propagated"); }
		catch (IllegalStateException expected) { }
		check(first.value == 5 && second.value == 6, "partial failure rolls back both setters");

		FrameWindow clock = new FrameWindow();
		check(clock.frame(0) == null, "initial frame starts sampling");
		FrameWindow.Sample sample = null;
		for (int frame = 1; frame <= 1000; frame++) sample = clock.frame(frame * 1_000_000L);
		check(sample != null && sample.fps() == 1000, "one interval per frame");
		near(sample.meanMillis(), 1, "mean interval");
		near(sample.worstMillis(), 1, "worst interval");
		check(clock.frame(1_000_000_000L) == null, "duplicate timestamp ignored");
		sample = clock.frame(3_000_000_000L);
		near(sample.meanMillis(), 2000, "long stall retained in mean");
		near(sample.worstMillis(), 2000, "long stall not hidden from worst frame");
		for (long frame = 1; frame <= 1000; frame++) sample = clock.frame(3_000_000_000L + frame * 1_000_000L);
		near(sample.worstMillis(), 1, "worst resets in next window");
		clock = new FrameWindow(); clock.frame(100);
		for (int frame = 1; frame <= 600; frame++) { var next = clock.frame(100L + frame * 1_800_000L); if (next != null) sample = next; }
		near(sample.meanMillis(), 1.8F, "1.8ms is preserved, not rounded or clamped");
		check(sample.fps() == 556, "1.8ms corresponds to 556 FPS");
		System.out.println("Core checks passed: " + assertions + " assertions");
	}
	private static void add(ProfileChanges changes, Setting setting, int target) {
		changes.add(() -> setting.owned, () -> setting.value, value -> setting.value = value, target);
	}
	private static void check(boolean condition, String message) {
		assertions++; if (!condition) throw new AssertionError(message);
	}
	private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < 0.001, message); }
}
