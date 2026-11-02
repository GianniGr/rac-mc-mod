# Development guide

This guide describes the local development environment, the Gradle tasks that are actually
available in this repository, the layout of the sources and the disc importer of
**Ratchet & Clank: Getting Wood**.

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
├── LICENSE                          # The MIT licence of the source code
├── gradlew / gradlew.bat            # Committed Gradle wrapper
├── gradle/wrapper/                  # Committed wrapper jar and pinned distribution
├── .github/workflows/ci.yml         # CI foundation (build + test on Java 21, Linux/Windows/macOS)
├── docs/development.md              # This file
├── src/main/java/com/github/giannigr/racmc/
│   ├── RacMcConstants.java          # Mod id / mod name, free of Minecraft imports
│   ├── RacMcMod.java                # @Mod entry point
│   ├── client/
│   │   ├── RacMcClient.java         # Client only: key mapping and the selection service
│   │   ├── RacMcClientInput.java    # Client only: opens the selection screen
│   │   └── screen/
│   │       └── IsoSelectionScreen.java  # The screen where the disc image is chosen
│   └── iso/
│       ├── Rac1DiscCatalog.java     # The supported revisions and their digests
│       ├── DiscProfile.java         # The identity of one supported revision
│       ├── Rac1DiscRevision.java    # PAL v2.00 and NTSC-U v1.00
│       ├── Rac1DiscImage.java       # Read only access to a validated disc image
│       ├── Iso9660DiscImage.java    # An ISO 9660 image behind that interface
│       ├── Rac1DiscValidator.java   # The validation of a candidate disc image
│       ├── Rac1IsoSelectionService.java  # Keeps the image the player selected
│       ├── Rac1IsoSelectionResult.java   # The outcome of a validation
│       ├── IsoValidationResult.java # What was found, and why it was refused
│       ├── IsoValidationStatus.java # The possible outcomes
│       ├── IsoImagePath.java        # The safety rules for paths inside an image
│       └── IsoFormatException.java  # A damaged or unexpected image
├── src/main/resources/assets/racmc/lang/en_us.json  # Translations of the client side
├── src/main/templates/META-INF/
│   └── neoforge.mods.toml           # Mod metadata template, expanded at build time
└── src/test/java/com/github/giannigr/racmc/
    ├── ModBootstrapMetadataTest.java # Focused checks for the metadata wiring
    ├── ModDescriptionTest.java        # Focused checks for the mod description
    ├── ModDisplayNameTest.java        # Focused checks for the canonical project name
    ├── ModLicenseTest.java            # Focused checks for the MIT licence
    ├── ModNamespaceTest.java          # Focused checks for the mod namespace
    └── iso/
        ├── SyntheticIso.java          # Builds synthetic ISO 9660 images for the tests
        ├── Rac1DiscValidatorTest.java # Focused checks for the validation of a disc image
        ├── IsoPathSafetyTest.java     # Focused checks for the handling of paths
        └── Rac1IsoSelectionServiceTest.java  # Focused checks for the selection service
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

`mod_license` holds `MIT`, the licence the project owner selected for the source code of the
project. The full text is in the `LICENSE` file. The licence covers the source code only; it
grants no rights to the original game assets.

## The disc importer

The importer is the part of the mod that reads the disc image of the player. It lives in
`com.github.giannigr.racmc.iso` and has no Minecraft imports, so it can be tested on a plain JVM.

### What a candidate image has to look like

`Rac1DiscValidator` accepts a file when all of the following hold:

1. the file is an ISO 9660 image with 2048 byte sectors (the primary volume descriptor at sector
   16, the standard identifier `CD001`, version 1);
2. the image contains a `SYSTEM.CNF` file that names a boot executable;
3. the boot executable is the one of a supported revision;
4. the boot executable has the size that the revision has; and
5. the boot executable has the SHA-256 digest that the revision has.

The revision is identified by the product code of the boot executable, which is what a PlayStation
2 disc stores in `SYSTEM.CNF`:

| Revision | Product code | Boot executable size | Boot executable SHA-256 |
| --- | --- | --- | --- |
| PAL v2.00 | `SCES_509.16` | 1388100 | `17f8a846329fd10798cb97847e9610e5eecd1867668420ed627b2292a96b122b` |
| NTSC-U v1.00 | `SCUS_971.99` | 1383028 | `e050581032e4bb3f20341307da5b69b76f1574910519155380ea771e55c3c0c9` |

Every other revision of the game - the PAL v1.00 release, the Japanese release and the demo discs -
is refused with an `UNSUPPORTED_REVISION` result. A disc of another game, a file that is not an
ISO 9660 image and a container format such as CHD are refused as well. A disc that is damaged or
whose boot executable was modified is refused with a `CORRUPT_IMAGE` result.

The values are the public facts of the retail discs, taken from the OpenRAC manifest
`games/rac1/game.json` (the `boot` section of each version, branch `develop`). The format of the
file system follows ECMA-119 and the layout of the disc follows the ReRAC format documentation
`docs/formats/disc_layout.md` (ISC licence). The importer is an independent Java implementation;
no code of either project was copied.

### What the importer does not do

- It does not extract anything. `Rac1DiscImage` only hands out the bytes of the files of the
  image; the extraction step comes later and is a separate milestone.
- It does not write to the image and does not copy it anywhere.
- It does not read the whole image. Only the primary volume descriptor, the root directory, the
  `SYSTEM.CNF` file and the boot executable are read, the last one in chunks for the digest.

### The client side

The player opens the selection screen with the **O** key, or with the key bound under
*Options → Controls → Key Mappings* under *Ratchet & Clank: Getting Wood*. The screen takes the
path of a local file, checks it and reports the outcome. The check runs on a worker thread and the
result is handed back to the client thread before it is shown.

Everything under `com.github.giannigr.racmc.client` is client only: the event subscribers are
registered with `Dist.CLIENT` and the screen is never referenced from common code, so a dedicated
server neither loads them nor opens the screen. The screen is opt-in and is **not** a first-launch
gate: the game starts normally whether or not a disc image was selected.

### The check of a real disc image

`RealDiscImageTest` is an opt-in check of a real disc image. It is skipped unless the path of an
image is given on the command line:

```bash
./gradlew test --rerun -Dracmc.isoImage=C:/path/to/the/disc.iso
```

An optional second property names the revision the image is expected to have:

```bash
./gradlew test --rerun -Dracmc.isoImage=C:/path/to/the/disc.iso -Dracmc.isoExpectedRevision="PAL v2.00"
```

The image is only read. It is never copied, moved, changed or written to, and no part of it ends
up in this repository or in a report of the test run.

With the permission of the project owner, the check was run against retail disc images of both
supported revisions - the European PAL v2.00 release and the North American NTSC-U v1.00 release.
Both were accepted and identified correctly, and the size of each image matched the size of the
retail disc that the catalogue describes. The importer is therefore verified against real images
for the two supported revisions, not only against synthetic ones.

The disc images themselves are not part of this repository and were not copied into it. The
check has to be repeated by every player who wants to verify a disc image of their own, with the
command above.

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

The ModDevGradle development task does not enforce the Minecraft EULA: the verified `runServer`
launch started, ran and stopped again without a `run/eula.txt` file in `run/`. That observation is
limited to this development task. A standalone dedicated server distribution is a different case
and still requires the EULA to be accepted before it starts.

`run/` is ignored by Git, so the generated run directories and their local state are never
committed.

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

Eight focused test classes exist today:

- `ModBootstrapMetadataTest` verifies that the mod id, mod name and mod version are identical in
  `gradle.properties`, in the generated `META-INF/neoforge.mods.toml` and in `RacMcConstants`, and
  that the generated metadata contains the fields NeoForge requires. It only reads files, so it
  runs on a plain JVM.
- `ModNamespaceTest` verifies that the mod id is a valid Minecraft resource namespace, because
  every future block, item, data file and translation key is addressed with it.
- `ModDescriptionTest` verifies that the multi line `description` of the generated
  `META-INF/neoforge.mods.toml` is not blank and that it matches the description in the metadata
  template. It reads the raw metadata text, because the minimal TOML reader of the metadata test
  always yields an empty string for a multi line `'''` value and therefore cannot detect an
  emptied description.
- `ModDisplayNameTest` verifies that the canonical project and mod name
  `Ratchet & Clank: Getting Wood` is used unchanged in `gradle.properties`, in `RacMcConstants`, in
  the generated metadata, in the README and in this guide, that the mod id `racmc` and the package
  `com.github.giannigr.racmc` did not change, and that the unofficial fan project disclaimer is
  still there.
- `ModLicenseTest` verifies that the `LICENSE` file holds the MIT licence, that `mod_license` and
  the `license` field of the generated metadata hold `MIT`, that the placeholder of the foundation
  is gone and that the licence only covers the source code.
- `Rac1DiscValidatorTest` verifies the validation of candidate disc images with synthetic ISO 9660
  images: the accepted revisions, the refused revisions of the game, discs of other games, files
  that are not ISO 9660 images, container formats such as CHD, unusable paths, a boot executable
  with the wrong size, a modified boot executable, malformed directory records, a directory record
  whose name could escape a directory and a directory that points outside of the image.
- `IsoPathSafetyTest` verifies the handling of paths: the paths of the files inside an image may
  not be absolute, may not contain a `..` segment, an empty segment, a drive separator or a control
  character, and a file of an image is read completely and only up to its own size.
- `Rac1IsoSelectionServiceTest` verifies that the selection service keeps a supported image,
  forgets it when the next candidate is refused, and that the catalogue holds exactly the two
  supported revisions with their published digests.

The suite currently contains 69 tests: 7 in `ModBootstrapMetadataTest`, 2 in `ModNamespaceTest`,
2 in `ModDescriptionTest`, 8 in `ModDisplayNameTest`, 8 in `ModLicenseTest`, 23 in
`Rac1DiscValidatorTest`, 11 in `IsoPathSafetyTest` and 8 in `Rac1IsoSelectionServiceTest`.

`RealDiscImageTest` is a further, opt-in check of a real disc image. It is skipped unless the path
of an image is given on the command line, and it is described in the section about the disc
importer above.

No test needs a game disc image to run. The images used by the tests are built by `SyntheticIso`
and contain no game data.

## Verified commands and results

Measured on the development machine (Windows 11, AMD Ryzen 9 9950X3D, NVIDIA GeForce RTX 5090,
Amazon Corretto OpenJDK 21.0.4 at `C:\Users\giann\.jdks\corretto-21.0.4`, Gradle 8.14.5 from the
committed wrapper). The machine wide default Java was not changed; `JAVA_HOME` was pointed at the
Java 21 installation for the Gradle process only. The build, the test suite, the CI command and
both game launches were additionally reproduced by an independent verification worker on the same
tree.

| Command | Result |
| --- | --- |
| `gradlew.bat tasks` | Listed the task groups reproduced above; the configuration succeeded with ModDevGradle 2.0.148 and NeoForge 21.1.265. |
| `gradlew.bat build` | `BUILD SUCCESSFUL`. Produced `build/libs/racmc-0.1.0.jar` containing `META-INF/neoforge.mods.toml` and the mod classes. The first run of the foundation downloaded and decompiled the Minecraft 1.21.1 artifacts (about 83 s of tool time, plus the 787 MiB asset download for the run configurations). |
| `gradlew.bat test` | `BUILD SUCCESSFUL`, 69 of 69 tests passed (7 in `ModBootstrapMetadataTest`, 2 in `ModNamespaceTest`, 2 in `ModDescriptionTest`, 8 in `ModDisplayNameTest`, 8 in `ModLicenseTest`, 23 in `Rac1DiscValidatorTest`, 11 in `IsoPathSafetyTest`, 8 in `Rac1IsoSelectionServiceTest`). |
| `gradlew.bat test --rerun` | `BUILD SUCCESSFUL`, 69 of 69 tests passed with the test task forced to re-execute. |
| `gradlew.bat clean build` | `BUILD SUCCESSFUL`, the same 69 of 69 tests passed after a clean. |
| `CI=true .\gradlew.bat --no-daemon build` | `BUILD SUCCESSFUL`. This is the command the Windows CI job runs, with the environment variables that GitHub Actions sets. |
| `gradlew.bat runClient` | The client window opened (title `Minecraft NeoForge* 1.21.1`), OpenGL 4.6 on the RTX 5090, the mod list contained the mod under the mod id `racmc` with the version `0.1.0` and the resource manager reloaded `mod/racmc`. The mod logged its start-up with the mod id `racmc` and the version `0.1.0`. The client was closed from its own window, which logged `Stopping!` and ended the Gradle task with `BUILD SUCCESSFUL`. The independent verification worker repeated the launch on the same tree and its second run also closed cleanly through the client window, with no error or exception in the log. This run was made on the foundation, before the display name of the project was changed. |
| `gradlew.bat runServer` | The dedicated server started Minecraft 1.21.1 / NeoForge 21.1.265, loaded `racmc` (the mod list showed the mod under the mod id `racmc` with the version `0.1.0`), listened on `*:25565` and reported `Done (0.720s)! For help, type "help"`. It was then stopped with the server console command `stop`, which saved the players and the world of every dimension (`Saving chunks for level .../minecraft:overworld`, `.../minecraft:the_nether`, `.../minecraft:the_end`, `All dimensions are saved`) and ended the Gradle task with `BUILD SUCCESSFUL` (exit code 0, 18 s). The final harness that delivered the `stop` input ran with `--no-configuration-cache`; see the note on the server console below. This run was made on the foundation, before the display name of the project was changed. |
| `CI=true gradlew.bat --no-daemon clean build` | `BUILD SUCCESSFUL`. This is the command the Windows CI job runs, with the environment variables that GitHub Actions sets. The first cold run took about 40 s; a run with a warm Gradle build cache took 5 s. |
| `CI=true gradlew.bat --no-daemon test --rerun` | `BUILD SUCCESSFUL`, 11 of 11 tests passed with the test task forced to re-execute on the foundation, before the importer was added. |
| `gradlew.bat test --rerun -Dracmc.isoImage=<path> -Dracmc.isoExpectedRevision=<revision>` | `BUILD SUCCESSFUL`, the opt-in check of the real disc image passed for the European PAL v2.00 release and for the North American NTSC-U v1.00 release. Both images were read in place, were accepted and were identified with the expected revision. The images were not copied into the repository and are not part of it. |

All of the measurements above were taken on Windows. The Linux and macOS build and test runs are
covered by the CI matrix but have not been executed yet, and the client and dedicated server
launches have only ever been verified on Windows.

Notes on the run observations:

- The first start of the dedicated server logs
  `Failed to load properties from file: server.properties` (a `NoSuchFileException`). This is
  vanilla's expected behaviour when `server.properties` does not exist yet; the server then writes
  the default file.
- The development server runs in offline mode and warns about it. Set `online-mode=false` in
  `run/server.properties` to connect a local client to it.
- The dedicated server console is not attached to the Gradle console by default, and the standard
  Gradle console does not control the server automatically. In the verified run the server was
  stopped by delivering the `stop` command through a custom input stream attached to the
  `standardInput` of the `runServer` task. Two Gradle limitations were observed with that
  harness: Gradle did not forward the custom standard input to the `JavaExec` task, and a custom
  `PipedInputStream` failed while the configuration cache was enabled, because the configuration
  cache supports only `System.in` as the task input stream. The same harness completed when the
  run was repeated with `--no-configuration-cache`.

## Continuous integration

`.github/workflows/ci.yml` builds the mod and runs the JUnit test suite on three operating
systems: `ubuntu-latest`, `windows-latest` and `macos-latest`. It runs for pushes to `main`, for
pull requests and on manual dispatch.

The Gradle project configuration is operating system neutral. Only the invocation of the wrapper
differs, because Windows uses the batch script and Linux and macOS use the shell script:

| Runner | Job | Gradle command |
| --- | --- | --- |
| Linux | `Build and test (ubuntu-latest, Java 21)` | `./gradlew --no-daemon build` |
| Windows | `Build and test (windows-latest, Java 21)` | `.\gradlew.bat --no-daemon build` |
| macOS | `Build and test (macos-latest, Java 21)` | `./gradlew --no-daemon build` |

All three jobs use the same Java 21 Temurin JDK from `actions/setup-java`, run with a 60 minute
timeout, and `fail-fast` is disabled so that every operating system reports its own result even
when another one fails.

Security properties of the workflow:

- `permissions: contents: read` and no repository secrets.
- `pull_request` is used, never `pull_request_target`, so untrusted pull request code never runs
  with a privileged token.
- No publishing or deployment step, and no `id-token` permission.
- Only `actions/checkout` (MIT) and `actions/setup-java` (MIT) are used.
  `gradle/actions/setup-gradle` is deliberately not used because that repository also bundles a
  proprietary component under a separate licence.

### What CI does and does not prove

CI is **build and test only**. The `build` task compiles the mod and runs the JUnit test suite.
The workflow never launches the Minecraft client or the dedicated server, so a green CI run says
nothing about whether the game starts.

Runtime validation is a separate, manual activity:

| Validation | Status |
| --- | --- |
| Build and unit tests on Windows | Verified locally (`gradlew.bat build`, `gradlew.bat test`) |
| Build and unit tests on Linux | **Not verified.** No Linux runner has executed this workflow yet |
| Build and unit tests on macOS | **Not verified.** No macOS runner has executed this workflow yet |
| Client launch (`runClient`) | Verified locally on **Windows only** |
| Dedicated server launch (`runServer`) | Verified locally on **Windows only** |

The Linux and macOS game launches are **unverified**: this project has only ever started the
client and the dedicated server on Windows. The CI matrix covers compilation and tests only and
must not be read as evidence of runtime support on those platforms.

The build command of the workflow was verified locally with the same environment that GitHub
Actions sets: `CI=true` and `--no-daemon` make ModDevGradle use its binary artifact pipeline
instead of decompiling Minecraft, and the build and all eleven tests of the foundation passed.
The Windows form of the command is the one that was executed; the Linux and macOS forms are the
shell script variant of the same task and have not been run.
**GitHub Actions has executed this workflow.** The first run of the matrix passed on Ubuntu,
Windows and macOS. The game runtime is still not covered by it.

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

No asset extraction, rendering or parsing library is part of this project. The disc importer uses
the Java standard library only, so no dependency was added for it.
