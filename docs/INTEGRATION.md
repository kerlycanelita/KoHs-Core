# Connecting a mod to KoHs Core

Core already recognises the KoHs mods without any of them being changed — it infers ownership from
what is installed. This guide is for going further: claiming a domain explicitly, contributing a
settings tab, and drawing from the shared particle budget.

Nothing here creates a hard dependency in either direction. A mod that ships a `CoreModule` still
loads perfectly with Core absent, because Fabric only instantiates entrypoints for keys something
actually asks for.

---

## 1. Compile against the API

Core is not on a Maven repository, so depend on the built jar directly. In your `build.gradle`:

```groovy
dependencies {
    // compileOnly, not implementation: your mod must still run without Core installed.
    compileOnly files("../KoHs-Core/build/libs/kohs-core-1.0.0.jar")
}
```

Add it to `suggests` rather than `depends` in your `fabric.mod.json`:

```json
"suggests": { "kohs_core": "*" }
```

## 2. Register the entrypoint

```json
"entrypoints": {
    "client": [ "dev.zymekoh.mymod.MyModClient" ],
    "kohs:core": [ "dev.zymekoh.mymod.MyCoreModule" ]
}
```

Core calls `onCoreReady` once, on the client thread, after its own setup finishes.

## 3. Write the module

```java
package dev.zymekoh.mymod;

import dev.zymekoh.kohscore.api.CoreContext;
import dev.zymekoh.kohscore.api.CoreModule;
import dev.zymekoh.kohscore.api.CoreSignals;
import dev.zymekoh.kohscore.api.Domain;
import dev.zymekoh.kohscore.api.DomainClaim;

public final class MyCoreModule implements CoreModule {

    private static final String MOD_ID = "my_mod";

    @Override
    public void onCoreReady(CoreContext context) {
        // Take a domain. Core stops writing it and greys out its own controls for it.
        context.claim(DomainClaim.exclusive(
                Domain.SCREEN_EFFECTS, MOD_ID, "My Mod",
                "my_mod.yield.screen_effects"));

        // React to the client struggling instead of measuring frame times yourself.
        context.bus().subscribe(CoreSignals.Performance.class, performance -> {
            MyEffects.setQuality(performance.pressure().budgetScale());
        });
    }
}
```

`claim` returns `false` when another mod already holds the domain exclusively. That is a real
answer, not an error — stand down rather than proceeding.

The `ownerModId` on a claim must match your own mod id. Core scopes each module's context to its
own id and refuses claims made under someone else's name.

Use `DomainClaim.shared(...)` when you only drive part of a domain and Core should keep managing the
rest. `reason` is a translation key shown on the greyed-out control, so the player learns who took
the setting and why.

### Domains

`PARTICLES` · `CLOUDS` · `GRAPHICS_QUALITY` · `VIEW_DISTANCE` · `ENTITY_CULLING` · `FRAME_PACING` ·
`INPUT_LATENCY` · `MEMORY` · `WEATHER` · `SCREEN_EFFECTS` · `INVENTORY_UI` · `ANCHOR_EFFECTS` ·
`DEATH_EFFECTS` · `HOTBAR_EFFECTS`

---

## 4. Share the particle budget

Any mod that spawns particles should ask first. Because every mod draws from one allowance, three of
them firing at once degrade together instead of each concluding it is still inside its own private
limit.

```java
int granted = context.particles().claim(48);
for (int i = 0; i < granted; i++) {
    level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
}

// Respect the player's per-type switches and the global kill switch.
if (context.particles().isTypeEnabled("minecraft:soul_fire_flame")) {
    ...
}

// Scale a burst instead of dropping it entirely.
int count = Math.round(baseCount * context.particles().burstScale());
```

Subscribe to `CoreSignals.ParticleBudget` to resize your own pools when the allowance changes,
rather than polling for it.

---

## 5. Contribute a settings tab

Two ways. Hand Core a list of options and inherit its look, its hover descriptions and its ownership
greying:

```java
context.addSettingsPage(new CoreSettingsPage() {
    @Override public String id() { return "my_mod"; }
    @Override public Component title() { return Component.translatable("my_mod.tab"); }
    @Override public Component description() {
        return Component.translatable("my_mod.tab.description");
    }

    @Override public List<CoreOption> options() {
        return List.of(new CoreOption.Toggle(
                Component.translatable("my_mod.option.glow"),
                Component.translatable("my_mod.option.glow.description"),
                () -> MyConfig.glow,
                value -> { MyConfig.glow = value; MyConfig.save(); }));
    }
});
```

Or return a `Screen` from `createScreen(parent)` to take over the whole pane and draw it yourself.
`CoreOption` also has `Slider`, `Choice` and `Action`.

---

## 6. Signals

Everything Core publishes on the bus:

| Signal | When | Carries |
| --- | --- | --- |
| `Performance` | once a second | FPS, frame time, heap ratio, pressure level |
| `ParticleBudget` | when the allowance changes | allowance per second, burst scale |
| `DomainOwnershipChanged` | on claim or release | the domain and its new owner, or `null` |
| `SettingsApplied` | after the player applies settings | nothing |

Delivery is synchronous on the publishing thread and unordered. A listener that throws is logged and
skipped, so one misbehaving mod cannot silence the others.

`PressureLevel` is `RELAXED`, `ELEVATED`, `HIGH` or `CRITICAL`, and `budgetScale()` gives the 1.0 to
0.1 multiplier to apply to your own effects.

---

## If you would rather change nothing

That is a supported outcome. `KnownSiblings` already maps the installed KoHs mods to the domains
they own, and `PriorityCompatibility` reserves the specific resources they touch — the two explosion
particle types Anchors budgets, for instance. Core yields to all of it today with those mods
untouched. Adopting the API turns an assumption into a fact and buys you the bus and a settings tab;
it is an upgrade, not a requirement.
