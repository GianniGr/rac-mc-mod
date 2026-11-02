# Ratchet & Clank Minecraft Mod

A community-made Minecraft Java Edition mod project inspired by **Ratchet & Clank**.

## Project status

**Foundation initialized.** The Minecraft mod project itself is set up and buildable; gameplay
features have not been implemented yet. The first milestone (M1) covers the asset import pipeline
and the first rendering of original assets.

## Vision

Build a Minecraft experience featuring Ratchet & Clank-inspired gameplay, with a focus on importing original game assets from a player's legally obtained game disc image rather than distributing those assets in the mod.

The planned development roadmap includes an asset-import pipeline, weapons and crates, a Gadgetron vendor, flyable spaceships, enemies, planetary exploration, and eventually spaceflight within the Solana Galaxy.

## Technical stack

The platform is fixed for the 1.21.1 line:

| Component | Choice |
| --- | --- |
| Minecraft | Java Edition **1.21.1** |
| Mod loader | **NeoForge 21.1.x** (LGPL-2.1) |
| Java | **21** (toolchain enforced by Gradle) |
| Build system | **Gradle** 8.14.5 via the committed Gradle wrapper, with ModDevGradle 2.0.148 |
| IDE | IntelliJ IDEA (Eclipse also works) |

Mod identifier: `racmc`. Java base package: `com.github.giannigr.racmc`.

## Building and running

A Java 21 JDK is required; the Gradle wrapper downloads the pinned Gradle distribution itself.

```bash
./gradlew build          # compile the mod and run the test suite (Windows: .\gradlew.bat build)
./gradlew test           # run the JUnit test suite only
./gradlew runClient      # launch the development client
./gradlew runServer      # launch the development dedicated server
```

The dedicated server run needs the Minecraft EULA accepted once, in the generated
`run/eula.txt` file (`eula=true`). The development run directories are ignored by Git.

See [`docs/development.md`](docs/development.md) for the full task list, the project layout and
the details of every verified command.

## Workflow

Development coordination and AI worker procedures are maintained separately in [`GianniGr/gianni-multi-ai-workflows`](https://github.com/GianniGr/gianni-multi-ai-workflows), under `rac-mc-mod/` once the setup PR has been merged.

## Legal

This is an unofficial fan project and is not affiliated with or endorsed by Insomniac Games or Sony Interactive Entertainment. Original game assets are not included in this repository.

## License

A source-code license has not yet been selected. Do not assume reuse or redistribution rights without permission.

No `LICENSE` file is present in this repository. The mandatory `license` field in
`META-INF/neoforge.mods.toml` currently carries the placeholder `All Rights Reserved`, which is
the upstream ModDevGradle default: it grants no reuse or redistribution rights and is **not** a
license selection. It is kept in `gradle.properties` as the single `mod_license` value so that the
owner's actual choice can be applied in one place before the mod is published or distributed.
