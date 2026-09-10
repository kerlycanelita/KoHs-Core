package dev.zymekoh.kohscore.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One-shot changes; undo never overwrites another owner's or the user's later edits. */
public final class ProfileChanges {

	private final List<Change<?>> pending = new ArrayList<>();
	private final List<Change<?>> applied = new ArrayList<>();
	public record Result(int changed, int skipped) { }

	public <T> void add(BooleanSupplier allowed, Supplier<T> getter, Consumer<T> setter, T target) {
		pending.add(new Change<>(allowed, getter, setter, target));
	}

	public Result apply() {
		if (!applied.isEmpty()) throw new IllegalStateException("Already applied");
		int skipped = 0;
		try {
			for (Change<?> change : pending) {
				if (!change.allowed.getAsBoolean()) { skipped++; continue; }
				if (change.apply()) applied.add(change);
			}
		} catch (RuntimeException failure) {
			try { undo(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
			throw failure;
		}
		return new Result(applied.size(), skipped);
	}

	public boolean canUndo() { return !applied.isEmpty(); }

	public Result undo() {
		int changed = 0;
		int skipped = 0;
		RuntimeException failure = null;
		for (int i = applied.size() - 1; i >= 0; i--) {
			try {
				if (applied.get(i).undo()) changed++; else skipped++;
			} catch (RuntimeException ex) {
				if (failure == null) failure = ex; else failure.addSuppressed(ex);
			}
		}
		applied.clear();
		if (failure != null) throw failure;
		return new Result(changed, skipped);
	}

	private static final class Change<T> {
		private final BooleanSupplier allowed;
		private final Supplier<T> getter;
		private final Consumer<T> setter;
		private final T target;
		private T before;
		private T actual;
		private Change(BooleanSupplier allowed, Supplier<T> getter, Consumer<T> setter, T target) {
			this.allowed = allowed; this.getter = getter; this.setter = setter; this.target = target;
		}
		private boolean apply() {
			before = getter.get();
			if (Objects.equals(before, target)) return false;
			try {
				setter.accept(target);
				actual = getter.get(); // Respect validation/clamping by vanilla or a renderer.
			} catch (RuntimeException failure) {
				try { if (allowed.getAsBoolean()) setter.accept(before); }
				catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
				throw failure;
			}
			return !Objects.equals(before, actual);
		}
		private boolean undo() {
			if (!allowed.getAsBoolean() || !Objects.equals(getter.get(), actual)) return false;
			setter.accept(before);
			return true;
		}
	}
}
