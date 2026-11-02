# Development guide

This guide describes the local development environment, the Gradle tasks that are actually
available in this repository, and the layout of the sources.

## Requirements

| Requirement | Value |
| --- | --- |
| JDK | 21 (Gradle enforces the Java 21 toolchain; the build fails on other JDKs) |
| Gradle | Supplied by the committed wrapper, pinned to 8.14.5 (`gradle/wrapper/gradle-wrapper.properties`) |
| Network | Required on the first build: Gradle, ModDevGradle, NeoForge and the Minecraft artifacts are downloaded |

The Gradle wrapper downloads the pinned distribution and verifies it against the
`distributionSha256Sum` recorded in `gradle/wrapper/gradle-wrapper.properties`.

Do not change the machine-wide Java default for this project. If the default JDK is not 21,
either point `JAVA_HOME` at a Java 21 installation for the session that runs Gradle, or let the
Gradle toolchain resolver provision a Java 21 toolchain (the
`org.gradle.toolchains.foojay-resolver-convention` plugin is applied in `settings.gradle`).

## Project layout

```
rac-mc-mod/
├── build.gradle                     # Build definition (ModDevGradle, runs, tests, metadata)
├── settings.gradle                  # Plugin management and root project name
├── gradle.properties                # Single source of truth for the mod identity and versions
├── gradlew / gradlew.bat            # Committed Gradle wrapper
├── gradle/wrapper/                  # Committed wrapper jar and pinned distribution
├── .github/workflows/ci.yml         # CI foundation (build + test on Java 21)
├── docs/development.md              # This file
├── src/main/java/com/github/giannigr/racmc/
│   ├── RacMcConstants.java          # Mod id / mod name, free of Minecraft imports
│   └── RacMcMod.java                # @Mod entry point
├── src/main/templates/META-INF/
│   └── neoforge.mods.toml           # Mod metadata template, expanded at build time
└── src/test/java/com/github/giannigr/racmc/
    ├── ModBootstrapMetadataTest.java # Focused checks for the metadata wiring
    └── ModNamespaceTest.java          # Focused checks for the mod namespace
```

Package conventions follow the NeoForge documentation:

- `mod_group_id` (`com.github.giannigr.racmc`) is the group id and the base package of the sources.
- The mod id (`racmc`) is the inner package and the namespace for all resources, blocks, items and
  data added later.
- Client-only and server-only code is not separated into its own source set at this stage. When
  the first client-only feature lands, the `client` / `server` split is introduced through the
  ModDevGradle run configuration, not by duplicating the source tree.

Generated and local-only directories (never committed):

| Path | Content |
| --- | --- |
| `build/` | Compiled classes, the generated mod jar, generated mod metadata |
| `.gradle/` | Gradle project cache |
| `run/` | Development client, dedicated server, game test server and data generation run directories |
| `src/generated/` | Data generator output (not used yet) |

## Mod metadata

`src/main/templates/META-INF/neoforge.mods.toml` is expanded into
`build/generated/sources/modMetadata/META-INF/neoforge.mods.toml` by the `generateModMetadata`
task, using the values from `gradle.properties`. Edit the template and `gradle.properties`, never
the expanded copy.

`mod_license` currently holds the upstream placeholder `All Rights Reserved` because NeoForge
requires a non-empty `license` field. No source-code license has been selected by the project
owner; this placeholder grants no rights and must be replaced with the owner's choice before the
mod is published or distributed.

## Gradle tasks

`./gradlew tasks` lists the following groups (verified on this repository):

| Group | Tasks |
| --- | --- |
| Build | `assemble`, `build`, `buildDependents`, `buildNeeded`, `classes`, `clean`, `jar`, `testClasses` |
| Verification | `check`, `test` |
| Documentation | `javadoc` |
| Mod development | `createLaunchScripts`, `downloadAssets`, `runClient`, `runData`, `runGameTestServer`, `runServer` |
| Mod development (internal) | `createMinecraftArtifacts`, `prepareClientRun`, `prepareServerRun`, `prepareGameTestServerRun`, `prepareDataRun`, `neoForgeIdeSync`, `jarJar`, `write*LegacyClasspath` |
| Help / IDE | `help`, `dependencies`, `dependencyInsight`, `properties`, `javaToolchains`, `projects`, `idea`, `cleanIdea`, `openIdea` |

There is no `runServerMultiplayer` or similar task: the dedicated server is the `runServer` task
and it uses the `run/` directory. `runGameTestServer` is configured but currently exits with an
error, because no game test is registered yet; it becomes usable with the first game test.

## Running the client and the dedicated server

```bash
./gradlew runClient
./gradlew runServer
```

The dedicated server stops immediately on the first start because the Minecraft EULA has not been
accepted. Accept it once by editing `run/eula.txt`:

```
eula=true
```

`run/` is ignored by Git, so the acceptance is a local development state and is never committed.

The server run uses `--nogui`. The development client runs as the offline `Dev` player and the
development server runs in offline mode. To connect the client to the local server, set
`online-mode=false` in `run/server.properties`.

## Tests

The focused tests live in `src/test/java` and run with the standard Gradle `test` task:

```bash
./gradlew test
```

ModDevGradle's NeoForge aware unit test support is enabled (`neoForge { unitTest { enable() } }`),
so the test source set has the mod and the Minecraft classes on its classpath and tests can use
Minecraft types directly.

Two focused test classes exist today:

- `ModBootstrapMetadataTest` verifies that the mod id, mod name and mod version are identical in
  `gradle.properties`, in the generated `META-INF/neoforge.mods.toml` and in `RacMcConstants`, and
  that the generated metadata contains the fields NeoForge requires. It only reads files, so it
  runs on a plain JVM.
- `ModNamespaceTest` verifies that the mod id is a valid Minecraft resource namespace, because
  every future block, item, data file and translation key is addressed with it.

## Verified commands and results

Measured on the development machine (Windows 11, AMD Ryzen 9 9950X3D, NVIDIA GeForce RTX 5090,
Amazon Corretto OpenJDK 21.0.4 at `C:\Users\giann\.jdks\corretto-21.0.4`, Gradle 8.14.5 from the
committed wrapper). The machine wide default Java was not changed; `JAVA_HOME` was pointed at the
Java 21 installation for the Gradle process only.

| Command | Result |
| --- | --- |
| `gradlew.bat tasks` | Listed the task groups reproduced above; the configuration succeeded with ModDevGradle 2.0.148 and NeoForge 21.1.265. |
| `gradlew.bat build` | `BUILD SUCCESSFUL`. Produced `build/libs/racmc-0.1.0.jar` containing `META-INF/neoforge.mods.toml` and the two mod classes. The first run downloaded and decompiled the Minecraft 1.21.1 artifacts (about 83 s of tool time, plus the 787 MiB asset download for the run configurations). |
| `gradlew.bat test` | `BUILD SUCCESSFUL`, 9 of 9 tests passed (7 in `ModBootstrapMetadataTest`, 2 in `ModNamespaceTest`). |
| `gradlew.bat runClient` | The client window opened (title `Minecraft NeoForge* 1.21.1`), OpenGL 4.6 on the RTX 5090, the mod list contained `Ratchet & Clank Mod 0.1.0 (racmc)` and the resource manager reloaded `mod/racmc`. The mod logged `RaC Mod initialised: id=racmc, name=Ratchet & Clank Mod, version=0.1.0`. The client was closed from its own window, which logged `Stopping!` and ended the Gradle task with `BUILD SUCCESSFUL`. |
| `gradlew.bat runServer` | The dedicated server started Minecraft 1.21.1 / NeoForge 21.1.265, loaded `racmc`, listened on `*:25565` and reported `Done (2.670s)! For help, type "help"`. It was then stopped with the server console command `stop`, which saved the worlds and ended the Gradle task with `BUILD SUCCESSFUL`. |

Notes on the run observations:

- The first start of the dedicated server logs
  `Failed to load properties from file: server.properties` (a `NoSuchFileException`). This is
  vanilla's expected behaviour when `server.properties` does not exist yet; the server then writes
  the default file.
- The development server runs in offline mode and warns about it. Set `online-mode=false` in
  `run/server.properties` to connect a local client to it.
- The dedicated server console is not attached to the Gradle console by default. To stop it from
  the console, feed it the `stop` command, for example by attaching the `standardInput` of the
  `runServer` task to a stream that contains `stop`.

## Continuous integration

`.github/workflows/ci.yml` runs `./gradlew --no-daemon build` on `ubuntu-latest` with a Java 21
Temurin JDK for pushes to `main` and for pull requests.

Security properties of the workflow:

- `permissions: contents: read` and no repository secrets.
- `pull_request` is used, never `pull_request_target`, so untrusted pull request code never runs
  with a privileged token.
- No publishing or deployment step.
- Only `actions/checkout` (MIT) and `actions/setup-java` (MIT) are used.

The build command of the workflow was verified locally with the same environment that GitHub
Actions sets: `CI=true` and `--no-daemon` make ModDevGradle use its binary artifact pipeline
instead of decompiling Minecraft, and the build and all nine tests passed in about 40 seconds.
The workflow itself has not been executed by GitHub Actions yet; the first run on the
repository is still pending.

## Dependency licences

| Dependency | Version | Licence |
| --- | --- | --- |
| NeoForge | 21.1.265 | LGPL-2.1 |
| ModDevGradle | 2.0.148 | LGPL-2.1 |
| Gradle (wrapper distribution) | 8.14.5 | Apache-2.0 |
| foojay-resolver-convention | 1.0.0 | Apache-2.0 |
| ParchmentMC mappings | 1.21.1 / 2024.11.17 | CC0-1.0 |
| JUnit Jupiter / JUnit Platform | 5.14.4 | EPL-2.0 |
| actions/checkout | v4 | MIT |
| actions/setup-java | v4 | MIT |

No asset extraction, rendering or parsing library is part of this foundation.
