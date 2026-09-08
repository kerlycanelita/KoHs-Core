# KoHs Core

[![GitHub](https://img.shields.io/badge/GitHub-KoHs--Core-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/KoHs-Core)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/KoHs-Core/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/kohs_core/icon.png" alt="KoHs Core icon" width="220">
</p>

**Optimisation layer and coordination bus for the KoHs mod family.**

Minecraft **26.1.2** · Fabric · Java 25 · client only.

Core does two jobs. It is an optimiser with a settings screen that replaces vanilla's Video
Settings, and it is the layer the other KoHs mods use to stay out of each other's way.

---

## The settings screen

Clicking **Options → Video Settings** opens Core instead of vanilla's screen. The vanilla screen is
still one click away from Core's footer, for anything Core deliberately does not surface.

Tabs run down the left with hover descriptions that type themselves out in the panel along the
bottom. Speed is a setting: the slider bottoms out at 0.1 ms per character, which finishes a
sentence inside a single frame, and defaults to 6 ms, which is about the fastest speed where the
animation is still visible at 60 Hz.

| Tab | What it holds |
| --- | --- |
| Performance | Live FPS and heap, adaptive budgets, the in-game overlay, menu particle density, typing speed |
| Graphics | Vanilla quality options plus two tiers below anything vanilla offers |
| Particles | Every registered particle type, searchable, each with a live preview |
| Animations | Global visual-animation governor: animated textures, entities, blocks, clouds and hands |
| Entities | Distance culling for entities, drops, distant sign text and text displays |
| Input | Raw mouse input, scroll handling, VSync and frame cap |
| Memory | When Core is allowed to do memory work |
| JVM Profile | Your real hardware, and the launch arguments that suit it |
| Integration | Which mod is currently driving each setting |
| KoHs Suite | Crystal Tweaks, KoHs Anchors, Herzium, Inventory Tweaks and KoHs Hand Position |

### Animations and KoHs Suite

The Animations tab is cosmetic-only: it never cancels a world tick, changes redstone timing or
touches packets. Animated atlas sprites keep their first uploaded frame, then stop GPU uploads when
their branch is disabled. Entity, dropped-item, block-entity, cloud and first-person hand animation
states are flattened at render-state extraction time, so the input path remains untouched. A small
24-frame sprite strip is included at `docs/animations-preview.gif` for documentation and support.

KoHs Suite discovers the five family members at runtime and also understands Mod Menu factories.
The gear is bright only when a real configuration screen is available; otherwise it is intentionally
dimmed with a tooltip explaining why.

### Particles

Built from `BuiltInRegistries.PARTICLE_TYPE`, so modded particles appear alongside the vanilla ones
and get a preview too. Search, per-type toggles, an all-on/all-off pair that acts on whatever the
search is currently showing, a global kill switch, a shared spawn budget and a distance cull.

The preview beside each name is a **stylised likeness, not the live simulation**. Real particles
need a `ClientLevel` and a world camera that an options screen has no business creating, so each
type is matched to a motion-and-colour recipe and animated from that: right colour, right
direction, right rhythm. The screen says so on hover rather than implying otherwise.

### Performance overlay

A four-line readout in the corner of your choice: frame rate, frame time, the worst frame of the
last second, and heap usage. It hides itself whenever F3 is open.

The worst-frame figure is the reason it exists. An average of 120 with one 40 ms frame every second
feels considerably worse than a steady 90, and an average alone cannot tell the two apart — which is
how people end up tuning the wrong setting.

### Distant world text

Minecraft 26.1.2 rebuilds render-state matrices for every visible line on signs and text displays,
on every frame. Busy server hubs can therefore saturate a laptop GPU as soon as the frame rate is
unlimited. Core keeps each sign or display present but skips only its text past 48 blocks by default.
The distance is configurable on the Entities tab, and `0` restores vanilla's unlimited distance.

This is presentation-only: it does not modify a block entity, interaction, input event or packet.

### Quality tiers

Beyond vanilla's Fast preset:

- **Reduced** — ambient occlusion, entity shadows, biome blending and leaf cutout off
- **Pixelated** — also drops mipmaps to zero and switches texture filtering to nearest-neighbour
- **Potato** — also pulls render and simulation distance to their minimums

Everything is written through vanilla's own `Options`, never by patching the renderer. That is what
keeps Core compatible with Sodium and Iris.

> **One thing this does not do.** Making terrain past a given chunk radius pixelated *while keeping
> nearby blocks smooth* needs a custom shader in the chunk pipeline. That is renderer patching, which
> this mod stays out of by design. What the Pixelated tier gives instead is global nearest-neighbour
> filtering plus a hard distance cut, which reaches a similar look from a different direction.

### What the sliders will not do to you

Vanilla's `OptionInstance.set` does not clamp an out-of-range value. It rejects it, logs an error and
substitutes the option's *default*. So a slider whose maximum is wrong does not merely fail — it
throws away whatever the player had set. Every slider here takes its bounds from the option itself
rather than from a constant: view distance, for one, is capped at 16 instead of 32 whenever the heap
is under a gigabyte, which is exactly the machine this mod exists for.

The same care applies to step size. Frame limit only stores multiples of ten, and mouse wheel
sensitivity is stored logarithmically, so its slider drives the exponent instead of the result —
otherwise nine tenths of the usable settings would be crammed into the first tenth of the track.

### JVM Profile

Reads the machine through OSHI — the same library Minecraft already ships for crash reports — and
produces three argument sets: **Latency**, **Balanced** and **Heavy modpack**.

Heap size is not picked from a table of round numbers. It is what remains after subtracting what the
OS needs to stay responsive and what the JVM spends outside the heap, scaled by how much headroom
the chosen goal keeps in reserve. Collector choice follows from Java version, core count and total
RAM: ZGC where its sub-millisecond pauses are worth the extra native memory, tuned G1 otherwise.
Every flag comes with the reason it is there.

> **Core cannot apply any of this.** Heap size and garbage collector are fixed when the JVM starts;
> no mod can change them from inside a running game. These are arguments to paste into your
> launcher. Core does not edit `launcher_profiles.json` and never will.

### Memory

The honest version: Core cannot make garbage collection faster, and it never asks for one.

An earlier version called `System.gc()` at moments it judged safe. That was a mistake worth
recording, because it produces precisely the symptom an optimiser exists to remove: a stop-the-world
pause that freezes every thread while the average frame rate still looks healthy. There is no moment
during play that is reliably safe for it, so the button is gone rather than hidden behind a warning.

What this tab actually controls is **allocation pressure** — how hard Core leans on the particle
budget as the heap fills and frame times stretch. The saving comes from objects that are never
created, which is cheaper than any collection strategy. The heap threshold decides when that
tightening begins; the adaptive budget decides how far it goes.

Heap size and collector are set at JVM start, which is what the JVM Profile tab is for.

---

## Coordination

Core owns every setting by default and gives it up the moment something better-qualified shows up.

**Domains.** Each slice of behaviour — particles, clouds, view distance, input latency and so on — is
a domain with exactly one owner. When another mod owns one, Core stops writing it and the matching
control stays on screen, dimmed and inert, with a tooltip naming the owner. It is not hidden,
because a player hunting for a setting that vanished is worse off than one who can see who took it.

**Two ways a domain gets claimed.**

1. *Inferred.* Core recognises mods that are already installed and assumes the ownership documented
   in `KnownSiblings` — your own mods and the common third-party optimisers alike. Nothing has to be
   changed in those mods for this to work.
2. *Explicit.* A mod registers a `kohs:core` entrypoint and claims what it wants. An explicit claim
   always replaces an inferred one, so adopting the API upgrades a guess into a fact.

**Reservations** are narrower than domains. KoHs Anchors runs its own budget for explosion
particles, but locking the entire particle page over two types would be absurd — so those two rows
are reserved and the other hundred-plus stay live.

**Mixins step aside too.** When a dedicated mod already owns a job, Core's mixin for that job is
never applied. Entity Culling present means Core's culling mixin does not load at all; there is
nothing to conflict with and nothing to debug.

Core registers mixins verified against 26.1.2 bytecode: the Video Settings redirect, particle spawn
gate, distance test, atlas animation governor, render-state animation flatteners and one `@Invoker`
accessor. See [docs/INTEGRATION.md](docs/INTEGRATION.md) to connect a mod. The project is released
under the MIT license; see [LICENSE](LICENSE).

---

## Building

Gradle must run on **JDK 25**, which is usually not the default `JAVA_HOME`:

```bash
./gradlew build -Dorg.gradle.java.home="C:\Program Files\Java\jdk-25.0.2"
```

Launch the client the same way:

```bash
./gradlew runClient -Dorg.gradle.java.home="C:\Program Files\Java\jdk-25.0.2"
```

Output lands in `build/libs/kohs-core-<version>.jar`. Mojang official mappings are Loom's default
from 26.1 onwards, so there is no `mappings` line and no refmap — the names in the source are the
real ones.

## License

MIT. See [LICENSE](LICENSE).

## Credits

Made by **zymekoh**.
