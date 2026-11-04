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
├── docs/assets.md                   # The conversion of the assets and the cache
├── src/main/java/com/github/giannigr/racmc/
│   ├── RacMcConstants.java          # Mod id / mod name, free of Minecraft imports
│   ├── RacMcMod.java                # @Mod entry point
│   ├── client/
│   │   ├── RacMcClient.java         # Client only: the gate, the menu button and the media
│   │   ├── render/
│   │   │   ├── Rac1ClientTextures.java  # The textures of the game in the texture manager
│   │   │   └── Rac1MeshRenderer.java    # The renderer of the meshes of the game
│   │   └── screen/
│   │       └── IsoSelectionScreen.java  # The screen where the disc image is chosen
│   ├── asset/
│   │   ├── Rac1AssetExtractor.java   # Reads the assets of a level out of the disc
│   │   ├── Rac1Mesh.java             # The geometry of a piece of scenery
│   │   ├── Rac1SkinnedMesh.java      # The geometry of a character and the joints it follows
│   │   ├── Rac1Skeleton.java         # The joints of a character and their bind matrices
│   │   ├── Rac1Animation.java        # One animation sequence of a character
│   │   ├── Rac1AnimationEvaluator.java # Turns the keys of a sequence into the matrices of the joints
│   │   ├── Rac1Texture.java          # A texture of the game
│   │   ├── Rac1TextureReader.java    # Reads a texture out of the core data and the palette
│   │   ├── TieClassReader.java       # Reads the classes of the scenery of the game
│   │   ├── MobyClassReader.java      # Reads the classes of the characters of the game
│   │   ├── MobySequenceReader.java   # Reads the animation sequences of the game
│   │   ├── VifCommandList.java       # Reads the command lists of the graphics chip of the console
│   │   └── Rac1CoordinateTransform.java # The change from the axes of the game to the axes of the blocks
│   ├── cache/
│   │   ├── Rac1CacheCodec.java       # The format of the cache file
│   │   ├── Rac1CacheStore.java       # The place where the cache is kept
│   │   ├── Rac1AssetCacheManager.java # The life of the cache: read, build, fail, retry
│   │   ├── Rac1CacheState.java       # The states of the cache
│   │   ├── Rac1CacheContent.java      # The assets that a cache holds
│   │   └── Rac1DiscIdentity.java     # The identity of the disc the assets come from
│   ├── disc/
│   │   ├── Rac1DiscTables.java       # The table of contents of the disc
│   │   ├── Rac1LevelHeader.java      # The header of a level of the game
│   │   ├── Rac1LevelData.java        # The container of the data of a level
│   │   ├── Rac1CoreIndex.java        # The index of the core data of a level
│   │   ├── Rac1LevelAssets.java      # The data of a level: the index, the core data, the palettes
│   │   ├── Rac1ClassEntry.java       # An entry of a class table of a level
│   │   ├── Rac1TextureEntry.java     # An entry of a texture table of a level
│   │   └── DiscFormatException.java  # A damaged or unexpected table
│   ├── wad/
│   │   ├── WadDecoder.java           # The decoder of the compressed streams of the game
│   │   └── WadFormatException.java   # A damaged or unexpected stream
│   └── iso/
│       ├── Rac1DiscCatalog.java     # The supported revisions and their digests
│       ├── DiscProfile.java         # The identity of one supported revision
│       ├── Rac1DiscRevision.java    # PAL v2.00 and NTSC-U v1.00
│       ├── Rac1DiscImage.java       # Read only access to a validated disc image
│       ├── Iso9660DiscImage.java    # An ISO 9660 image behind that interface
│       ├── Rac1DiscValidator.java   # The validation of a candidate disc image
│       ├── Rac1IsoSelectionService.java  # Keeps the image the player selected
│       ├── Rac1IsoSelectionResult.java   # The outcome of a validation
│       ├── Rac1MediaManager.java    # The state of the media and the background verification
│       ├── Rac1MediaConfig.java     # The remembered path of the image
│       ├── Rac1MediaGate.java       # The rule that decides whether the game may be entered
│       ├── Rac1MediaState.java      # The states of the media
│       ├── IsoValidationResult.java # What was found, and why it was refused
│       ├── IsoValidationStatus.java # The possible outcomes
│       ├── IsoVerificationProgress.java # The progress of a running verification
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
    ├── asset/
    │   ├── MobyClassReaderTest.java   # Focused checks for the reader of the characters
    │   ├── MobySequenceReaderTest.java # Focused checks for the reader of the sequences
    │   ├── SyntheticMobySequence.java  # The animation sequences that the checks are built from
    │   ├── Rac1AnimationEvaluatorTest.java # Focused checks for the skinning of the meshes
    │   ├── Rac1CoordinateTransformTest.java # Focused checks for the change of the axes
    │   ├── Rac1TextureReaderTest.java # Focused checks for the reader of the textures
    │   ├── TieClassReaderTest.java   # Focused checks for the reader of the scenery
    │   ├── VifCommandListTest.java   # Focused checks for the commands of the graphics chip
    │   ├── RealDiscAssetTest.java    # The opt-in check of the conversion of a real disc image
    │   └── DiscFactsDiagnosticTest.java # The opt-in check of the facts the demo of the Blaster uses
    ├── cache/
    │   ├── SyntheticAssets.java       # The assets that the checks of the cache use
    │   ├── Rac1CacheCodecTest.java    # Focused checks for the format of the cache file
    │   ├── Rac1CacheStoreTest.java    # Focused checks for the place of the cache
    │   └── Rac1AssetCacheManagerTest.java # Focused checks for the life of the cache
    ├── client/
    │   ├── Rac1BlasterDemoTest.java   # Focused checks for the pose of the demo of the Blaster
    │   └── render/
    │       └── Rac1MeshRendererTest.java # Focused checks for the way the triangles are written
    ├── disc/
    │   └── Rac1CoreIndexTest.java     # Focused checks for the reader of the core index
    ├── wad/
    │   └── WadDecoderTest.java       # Focused checks for the decoder of the compressed streams
    └── iso/
        ├── SyntheticIso.java          # Builds synthetic ISO 9660 images for the tests
        ├── SyntheticDisc.java         # A synthetic image with the profile that describes it
        ├── Rac1DiscValidatorTest.java # Focused checks for the validation of a disc image
        ├── IsoPathSafetyTest.java     # Focused checks for the handling of paths
        ├── Rac1IsoSelectionServiceTest.java  # Focused checks for the selection service
        ├── Rac1MediaManagerTest.java  # Focused checks for the state of the media
        ├── Rac1MediaGateTest.java     # Focused checks for the rule of the gate
        └── RealDiscImageTest.java     # The opt-in check of a real disc image
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
4. the whole image has the exact size that the revision has;
5. the boot executable has the size that the revision has;
6. the boot executable has the SHA-256 digest that the revision has; and
7. the whole image has the SHA-1 digest that the revision has.

The last three checks make sure that the image is the unmodified retail disc and not a copy that
was changed somewhere. The digest of the whole image is the strong one: a change anywhere in the
4.2 GB of the image changes it, while the digest of the boot executable only covers the part of
the image that the boot program occupies.

The revision is identified by the product code of the boot executable, which is what a PlayStation
2 disc stores in `SYSTEM.CNF`:

| Revision | Product code | Image size | Image SHA-1 | Boot executable size | Boot executable SHA-256 |
| --- | --- | --- | --- | --- | --- |
| PAL v2.00 | `SCES_509.16` | 4214784000 | `ef304c641c7cd426ba492707dda5974679befbc0` | 1388100 | `17f8a846329fd10798cb97847e9610e5eecd1867668420ed627b2292a96b122b` |
| NTSC-U v1.00 | `SCUS_971.99` | 4214095872 | `aee9275384f739c9b24cd5de6f7e200292db7b01` | 1383028 | `e050581032e4bb3f20341307da5b69b76f1574910519155380ea771e55c3c0c9` |

The size and the SHA-1 of the whole image are the public facts of the Redump records of the two
retail discs:

- PAL v2.00, Redump disc 4254, `https://redump.org/disc/4254/`: 4214784000 bytes,
  SHA-1 `ef304c641c7cd426ba492707dda5974679befbc0`.
- NTSC-U v1.00, Redump disc 5362, `https://redump.org/disc/5362/`: 4214095872 bytes,
  SHA-1 `aee9275384f739c9b24cd5de6f7e200292db7b01`.

The same values are published in the OpenRAC manifest `games/rac1/game.json` (the `disc` section of
each version), which is where the digest of the boot executable comes from (the `boot` section).
The format of the file system follows ECMA-119 and the layout of the disc follows the ReRAC format
documentation `docs/formats/disc_layout.md` (ISC licence). The importer is an independent Java
implementation; no code of either project was copied.

Every other revision of the game - the PAL v1.00 release, the Japanese release and the demo discs -
is refused with an `UNSUPPORTED_REVISION` result. A disc of another game, a file that is not an
ISO 9660 image and a container format such as CHD are refused as well. A disc that is damaged or
that was modified is refused with a `CORRUPT_IMAGE` result.

### What the importer does not do

- It does not extract anything on its own. `Rac1DiscImage` only hands out the bytes of the files
  of the image and the bytes of its sectors; the conversion of the assets lives in
  `com.github.giannigr.racmc.asset` and is described in `docs/assets.md`.
- It does not write to the image and does not copy it anywhere. The configuration of the mod
  stores the path of the image and a few facts about it, never any part of the image.
- It does not keep the image in memory. The image is read in chunks of one megabyte for the
  digest, so the memory that is used does not depend on the size of the image. The rest of the
  verification reads only the primary volume descriptor, the root directory, the `SYSTEM.CNF` file
  and the boot executable.

### The client side and the gate

The disc image is mandatory. The game cannot be entered before a verified disc image is
configured, and the selection screen belongs to the main menu:

- The main menu shows a **Disc image** button in the lower left corner that opens the selection
  screen. The screen takes the path of a local file, checks it and reports the outcome.
- While no verified disc image is configured, the main menu does not open either. It is replaced
  by the selection screen, which cannot be dismissed: there is no back button, the escape key does
  nothing and the main menu it replaces never opens. The only ways out are a verified disc image,
  the settings of the game and quitting the game.
- The screens that lead into a world or into a multiplayer server - the singleplayer screen, the
  screen that creates a world and the server list - do not open either. The selection screen takes
  their place and explains what is missing.
- There is no key mapping for the screen and no way to open it from inside a world. The screen
  cannot remove the configured image either: the path of an image is only written to the
  configuration after the image was verified completely, and a failed attempt leaves the previous,
  valid image in place.

The selection screen lets the player pick the disc image with the file chooser of the system. As
soon as a file is picked, the check of that file starts on a worker thread; the screen shows how far
the reading of the image has come and reports the outcome. There is no separate button that starts
the check.

The game itself turns the graphical user interface of Java off in its entry point with
`java.awt.headless=true`, because the game does not need one. The file chooser does need one, so the
mod turns it back on before the chooser is used for the first time. The Java runtime reads the
property when it is asked for the first time, so the change has to happen early, which it does in
the static initialiser of `RacMcClient`.

`Rac1MediaManager` holds the state of the media and does all of its work on a worker thread, so
neither the verification of a 4.2 GB image nor the reading of its digest blocks the screen. The
file chooser of the system runs on the thread of the user interface toolkit of Java and hands the
chosen file back to the thread of the game.

The path of the verified image and a few facts about it - the revision, the size, the digest and
the time the file was changed - are written to `config/racmc/racmc-media.properties`, in the
configuration directory of the game. The file holds the keys `path`, `revision`, `imageSize`,
`imageSha1` and `modifiedTime` and nothing else; no part of the image is stored anywhere. When the
game starts again, that file is verified again in the background, so the player does not have to
pick the image again. The screen says that the configured image is being verified and shows how far
the reading has come. The game stays closed until the check is over, and it stays closed for good
when the file is missing or was changed - the path in the configuration is kept, so that the player
can replace it with a valid image.

Everything under `com.github.giannigr.racmc.client` is client only: the event subscribers are
registered with `Dist.CLIENT` and the screen is never referenced from common code, so a dedicated
server neither loads them nor opens the screen.

### Quick play and other ways around the gate

A quick play launch starts a world or joins a server before the mod is loaded, so the screens above
cannot stop it. Such a world is left again on the next client tick: the mod closes the connection
to the server and sends the player back to the selection screen. The game therefore fails closed on
that path as well - a world is never played and a server is never played on without a verified disc
image - and the player is asked for the disc image instead.

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
Both were accepted and identified correctly: the size and the SHA-1 digest of each image matched
the values of the Redump record of the disc, and the digest of the boot executable matched as
well. The importer is therefore verified against real images for the two supported revisions, not
only against synthetic ones.

The disc images themselves are not part of this repository and were not copied into it. The
check has to be repeated by every player who wants to verify a disc image of their own, with the
command above.

## The assets of the game and the cache

The importer only proves that the disc image of the player is the disc of the game. The assets of
the game - the geometry, the textures and the animation of the characters - live in the data of the
levels, which the game reads by absolute sector. `com.github.giannigr.racmc.disc` and
`com.github.giannigr.racmc.asset` read that data and turn it into the format the game of blocks
uses. `docs/assets.md` describes the whole path, the format facts it is based on and the sources
those facts come from.

The converted assets are kept in a local cache file (`com.github.giannigr.racmc.cache`). The file
is written by the player, into the cache directory of the mod, and it is never part of this
repository.

The cache is part of the gate: the game may only be entered when the disc image was verified **and**
the cache holds all of the assets. A cache that is being built, a cache that failed and a cache that is
missing all keep the game closed, so the game never runs with assets that are missing or only half
there. The selection screen shows the step and the share of the work that is done, reports what
went wrong when the conversion failed and offers to try again.

The cache holds three assets: a piece of the scenery (the tie class 452 of Novalis), the hero (the
moby class 0, with his skeleton and his 88 animation sequences) and his Blaster (the gadget class
168, which is a compressed stream of its own in the core data of the level).

The mod shows the imported assets in the game. The demo screen draws the hero with his Blaster in
his hand: the hero is posed with his stance of the weapon, the sequence 56, which the ReRAC project
measured in the definitions of the items of the game, and the Blaster with its own imported
sequences - the sequence 1 for the pose it is held in and the sequence 4 for the shot, the sequence
the game blends to when the weapon fires. The Blaster hangs on the joint of the skeleton of the hero
that the game hangs the items of the hand on. That joint is read out of the class of the hero, from
the first list of its joint lists, whose chain ends at the joint 56, and the weapon is placed with
the pose of that joint and with no matrix of its own, the way the game places it.

The demo is reachable in three ways: the setup screen, which the client shows when it starts,
draws the hero with the Blaster as long as the cache holds the assets; the main menu has a
**Blaster test** button; and the key **B** opens the demo screen from anywhere in the game.

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

Twenty-eight focused test classes exist today:

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
- `Rac1DiscValidatorTest` verifies the validation of candidate disc images with synthetic ISO 9660
  images: the accepted revisions, the refused revisions of the game, discs of other games, files
  that are not ISO 9660 images, container formats such as CHD, unusable paths, a boot executable
  with the wrong size, a modified boot executable, an image that was changed outside of the boot
  executable, an image whose size is not the size of the supported disc, malformed directory
  records, a directory record whose name could escape a directory and a directory that points
  outside of the image. It also verifies that the verification reads the whole image and reports
  its progress.
- `ModDisplayNameTest` verifies that the canonical project and mod name
  `Ratchet & Clank: Getting Wood` is used unchanged in `gradle.properties`, in `RacMcConstants`, in
  the generated metadata, in the README and in this guide, that the mod id `racmc` and the package
  `com.github.giannigr.racmc` did not change, and that the unofficial fan project disclaimer is
  still there.
- `ModLicenseTest` verifies that the `LICENSE` file holds the MIT licence, that `mod_license` and
  the `license` field of the generated metadata hold `MIT`, that the placeholder of the foundation
  is gone and that the licence only covers the source code.
- `IsoPathSafetyTest` verifies the handling of paths: the paths of the files inside an image may
  not be absolute, may not contain a `..` segment, an empty segment, a drive separator or a control
  character, and a file of an image is read completely and only up to its own size.
- `Rac1IsoSelectionServiceTest` verifies that the selection service keeps a supported image,
  forgets it when the next candidate is refused, and that the catalogue holds exactly the two
  supported revisions with their published digests and sizes.
- `Rac1MediaManagerTest` verifies the state of the media: without a configured image the game
  stays closed, a verified image opens the gate and is remembered, the remembered image is
  verified again when the game starts, a missing or changed image sends the game back to the gate,
  a failed replacement does not remove the valid image, the verification runs on a worker thread,
  it reports how far it has come, and the configuration holds the path of the image and nothing
  else.
- `Rac1MediaGateTest` verifies the rule that decides whether the game may be entered: only a
  verified image opens it, the selection screen can only be dismissed with a verified image, a
  world that was entered without one has to be left again, the screens that lead into a world or
  into a server are blocked while the main menu is not, and the cache of the assets has to be ready
  as well before the gate opens.
- `WadDecoderTest` verifies the decoder of the compressed streams of the game with streams that
  were built by hand: literals, matches with little literals, the dummy packet and the block
  marker, and the streams that are refused because they hold two literal packets in a row, because
  they are not streams at all, because they claim to be longer than they are, because they decode
  to the wrong size or because a match reaches before the start of the output.
- `TieClassReaderTest` verifies the reader of the classes of the scenery with a class that was
  built by hand: the vertices, the texture coordinates, the triangles of the strip of the packet,
  and the classes that are refused because their header is too short, because their packet table
  lies outside of the class, because their vertices do not fit into their region or because their
  strip does not start where the graphics packet of the console says.
- `Rac1CacheCodecTest` verifies the format of the cache file: a cache that was written can be read
  back, the file starts with the magic of the format, the identity of the disc is written into the
  file, and a cache whose bytes were changed, that was cut short, that belongs to another disc,
  that was written for another version of the schema or that is not a cache file at all is refused.
- `Rac1CacheStoreTest` verifies the place where the cache is kept: a cache that was written can be
  read back, there is no cache before one was written, a written cache leaves no file of a half
  written cache behind, a damaged cache is refused, the cache of another disc is not used, a cache
  can be thrown away and every disc gets a file of its own.
- `Rac1AssetCacheManagerTest` verifies the life of the cache: a cache that holds all of the assets
  is used, a cache that is damaged is not used, a disc image that is missing or that is not a disc of
  the game is reported, a failure can be tried again and the manager reports the step it is at.

- `MobyClassReaderTest` verifies the reader of the classes of the characters with a class that was
  built by hand: the joint lists of the class, which name the chain of joints the items of the hero
  hang on, a class without joint lists, a joint list that lies outside of the class, a joint list
  with an implausible size, and the recognition of a joint whose bind pose collapsed to a point.
- `MobyMeshTest` verifies the reader of the mesh of a class with more than one packet: the indices
  of the second packet name the vertices of the second packet and not the vertices of the first
  one, every index of the mesh names a vertex of the mesh, the triangles of the two packets are two
  different triangles that stand above each other, and an index that names a vertex that does not
  exist is refused. The check fails when the indices of the packets are not moved onto the
  vertices of the class, which is what the reader had to be fixed for.
- `MobySequenceReaderTest` verifies the reader of the animation sequences with a sequence that was
  built by hand: the times of the keys, the quaternions of the joints, the records of the
  translations and of the scales, the key that is dropped when it has the time of the key before it
  - with the records of its channels, which have to be dropped with it - and the sequences that are
  refused because they have no keys, because the payload of a key does not fit or because the key
  uses the special format of the game. The key at the end of a sequence that repeats itself is
  held as well: it holds the pose of the first key, so it holds the translations and the scales of
  the first key as well as its rotations. The check fails when the key at the end of the sequence
  holds no records of the channels, which would leave the pose of the end of the cycle a unit away
  in the translation and half a unit away in the scale.
- `Rac1AnimationEvaluatorTest` verifies the skinning of the meshes: at the bind pose the skinning
  matrices are the identity and the vertices do not move, a key that turns a joint turns the
  vertices that follow it, the chain of the joints is followed, a scale that only reaches the joint
  itself does not reach its children and the pose of the chain of the hand does not hold it. The
  checks spell out the convention of the game of the disc, so that a change of the convention in
  the evaluator is noticed. The cycle of a sequence that repeats itself is held as well: the cycle
  is as long as the last key of the sequence, the pose is the same after a whole cycle, the joint
  does not stand still at the end of the cycle and a sequence that does not repeat itself holds its
  last key. The check fails when the cycle is made longer, which would hold the pose of the first
  key for a whole key interval at the end of every cycle. A sequence that is read by the reader of
  the game and holds the records of its channels is checked as well: the pose at the end of its
  cycle is the pose at the start of it, because the key at the end of the sequence holds the whole
  pose of the first key.
- `Rac1CoordinateTransformTest` verifies the change of the coordinate systems: the up axis of the
  game becomes the up axis of the game of blocks, the change of the axes keeps the length of a
  direction, a direction is not scaled and a point is scaled with the size of a unit.
- `Rac1TextureReaderTest` verifies the reader of the textures: the colours of the palette of the
  game become the pixels of the game of blocks, and the textures whose pixels or whose palette lie
  outside of the data and the textures without a size are refused.
- `VifCommandListTest` verifies the reader of the command lists of the graphics chip of the console:
  the unpack commands are found, the unpack of the coordinates of the texture and the unpack of the
  indices of the triangles are told apart, an unpack without a number of elements holds 256 of them
  and a command that runs past the end of its list is refused.
- `Rac1CoreIndexTest` verifies the reader of the core index of a level with an index that was built
  by hand: the tables of the classes, of the gadgets and of the animation sequences of the hero, the
  size of the core data and the size of the blocks of it, and the indexes that are refused because
  they are too short, because they have no size or because one of their tables points outside of
  them.
- `Rac1MeshRendererTest` verifies the way the renderer writes the triangles of a mesh into the
  buffer of the game: a triangle is written as a quad of four vertices, whose fourth vertex is the
  first vertex of the triangle, because the kinds of triangles of the game of blocks are drawn in
  quads and a triangle of three vertices would be cut in two. The grouping of the triangles by
  their material is held as well: the triangles of a mesh are grouped into one run per material,
  the triangles of a run keep their order, a material that is out of range does not read outside
  of the textures, a mesh without triangles has no run, and a material that is missing is held in
  the run of the missing texture instead of reading outside of the array of the textures.
- `Rac1BlasterDemoTest` verifies the pose of the demo of the Blaster: the hero moves while his
  sequence runs, the pose of the hero is the same after a whole cycle of his sequence, the hero does
  not stand still at the end of his cycle and he is not torn apart when his sequence repeats itself.
  The weapon is placed on the pose of the joint of the hand and on no matrix of its own, it is held
  with the sequence of the held weapon, it plays the sequence of the shot once and it is blended
  into the shot and out of it again. The checks fail when the cycle of the sequence is made longer,
  when the weapon is moved by a matrix of its own or when the sequence of the shot is played over
  and over again.
- `RealDiscAssetTest` is the opt-in check of the conversion of the assets of a real disc image: the
  scenery, the hero and the Blaster are converted with the numbers of the vertices, of the
  triangles, of the joints, of the sequences and of the textures that were measured on the disc,
  every vertex of the hero and of the Blaster is used by a triangle of it, the triangles of the
  hero and of the Blaster cover the whole model, the chain of the hand of the hero ends at the
  joint 56 and the skinning does not tear the model of the hero apart. The joint lists of the hero,
  the arm of the weapon, the facing of the hero, the sequences of the held weapon and of the shot
  and the direction of the wrist of the hero in the stance of the weapon are held as well.
- `DiscFactsDiagnosticTest` is the opt-in check of the facts of a real disc image that the demo of
  the Blaster relies on: the joint lists of the class of the hero name the arm of the weapon, the
  class of the Blaster holds the chain of its muzzle, and the hero of the game holds the idle and
  the stance of the weapon with the lengths that the demo uses.

The suite currently contains 223 tests: 7 in `ModBootstrapMetadataTest`, 2 in `ModNamespaceTest`,
2 in `ModDescriptionTest`, 8 in `ModDisplayNameTest`, 8 in `ModLicenseTest`, 26 in
`Rac1DiscValidatorTest`, 11 in `IsoPathSafetyTest`, 9 in `Rac1IsoSelectionServiceTest`, 10 in
`Rac1MediaManagerTest`, 8 in `Rac1MediaGateTest`, 8 in `WadDecoderTest`, 5 in `TieClassReaderTest`,
5 in `MobyClassReaderTest`, 8 in `MobySequenceReaderTest`, 6 in `MobyMeshTest`, 12 in
`Rac1AnimationEvaluatorTest`, 7 in `Rac1CoordinateTransformTest`, 5 in `Rac1TextureReaderTest`,
6 in `VifCommandListTest`, 10 in `Rac1CoreIndexTest`, 8 in `Rac1MeshRendererTest`, 8 in
`Rac1BlasterDemoTest`, 10 in `Rac1CacheCodecTest`, 7 in `Rac1CacheStoreTest`, 6 in
`Rac1AssetCacheManagerTest`, 17 in `RealDiscAssetTest`, 3 in `DiscFactsDiagnosticTest` and 1 in
`RealDiscImageTest`. 202 of them pass, and twenty-one of them, the opt-in checks of a real disc
image, are skipped unless they are asked for.

`RealDiscImageTest`, `RealDiscAssetTest` and `DiscFactsDiagnosticTest` are the opt-in checks of a
real disc image. They are skipped unless the path of an image is given on the command line, and
they are described in the section about the disc importer above.

No test needs a game disc image to run. The images used by the tests are built by `SyntheticIso`
and contain no game data.

## Verified commands and results

Measured on the development machine (Windows 11, AMD Ryzen 9 9950X3D, NVIDIA GeForce RTX 5090,
Amazon Corretto OpenJDK 21.0.4 at `C:\Users\giann\.jdks\corretto-21.0.4`, Gradle 8.14.5 from the
committed wrapper). The machine wide default Java was not changed; `JAVA_HOME` was pointed at the
Java 21 installation for the Gradle process only. The build, the test suite, the CI command and
both game launches were additionally reproduced by an independent verification worker on the same
tree.

Every row of the table names the tree it was measured on. The rows of the foundation were
measured on the tree of the foundation, before the importer and the demo of the Blaster were
added. The rows of the tree of the demo of the Blaster are the report of the implementation
worker P03 of that tree, except the rows that name this revision, which were measured on it. The
rows that name a run with a real disc image were not repeated for this revision: the opt-in
checks of a real disc image are the scope of the independent worker T03, and the authorized
directory of the disc image does hold the images of the two retail releases, so they are not
independently verified here rather than impossible to run.

| Command | Result |
| --- | --- |
| `gradlew.bat tasks` | Listed the task groups reproduced above; the configuration succeeded with ModDevGradle 2.0.148 and NeoForge 21.1.265. |
| `gradlew.bat build` | `BUILD SUCCESSFUL`. Produced `build/libs/racmc-0.1.0.jar` containing `META-INF/neoforge.mods.toml` and the mod classes. The first run of the foundation downloaded and decompiled the Minecraft 1.21.1 artifacts (about 83 s of tool time, plus the 787 MiB asset download for the run configurations). |
| `gradlew.bat test` | `BUILD SUCCESSFUL`, 189 tests in all: 177 passed and the twelve opt-in checks of a real disc image were skipped. |
| `gradlew.bat test --rerun` | `BUILD SUCCESSFUL`, the same 189 tests in all: 177 passed and 12 skipped, with the test task forced to re-execute. |
| `gradlew.bat clean build` | `BUILD SUCCESSFUL`, the same 189 tests in all: 177 passed and 12 skipped after a clean. |
| `CI=true .\gradlew.bat --no-daemon build` | `BUILD SUCCESSFUL`. This is the command the Windows CI job runs, with the environment variables that GitHub Actions sets. |
| `gradlew.bat runClient` | The client window opened (title `Minecraft NeoForge* 1.21.1`). With no disc image configured the main menu did not open: the log shows `The disc image gate keeps the main menu closed until a disc image is configured.` and `The disc image picker was opened and cannot be dismissed.`, and the screen shows the selection screen with the button *Pick disc image* and without a back button. The escape key was sent to the window and the log shows `The disc image picker ignored the escape key.`; the screen stayed open and no world was prepared. The client was closed through its own window, which logged `Stopping!` and ended the Gradle task with `BUILD SUCCESSFUL`. |
| `gradlew.bat runClient` and the *Pick disc image* button | The file chooser of the system opened in its own window (`The file chooser of the system is opened.`, `headless=false`). A retail disc image was picked in it, the check of the whole image ran on a worker thread and the log shows `The disc image was verified: PAL v2.00.`. The path of the image and the facts about it were written to `run/config/racmc/racmc-media.properties` with the keys `path`, `revision`, `imageSize`, `imageSha1` and `modifiedTime`; the file holds no part of the image. |
| `gradlew.bat runClient` after that | The game read the configuration, verified the image again in the background (`The disc image was verified: PAL v2.00.` about two seconds after the start) and opened the main menu. The player did not have to pick the image again. |
| `gradlew.bat runClient --args="--quickPlaySingleplayer <world>"` | The client started with a quick play entry for a world. The log shows `Starting integrated minecraft server version 1.21.1` and then `The disc image gate leaves the world that was entered without a verified disc image.`: the world was entered, the mod closed the connection and sent the player back to the selection screen, which the log reports with `The disc image picker was opened and cannot be dismissed.`. The world was not played on. |
| `gradlew.bat runClient --args="--quickPlayMultiplayer 127.0.0.1:25565"` | The client started with a quick play entry for the dedicated server of the development environment, which listens on the loopback interface only. The log shows `Connecting to 127.0.0.1, 25565` and the server logs `Dev joined the game`: the connection was made and the player was in the world. The mod closed it again on the next tick (`The disc image gate leaves the world that was entered without a verified disc image.`), kept the main menu closed and sent the player back to the selection screen. No other server was contacted. |
| `gradlew.bat runServer` | The dedicated server started Minecraft 1.21.1 / NeoForge 21.1.265, loaded `racmc` (the mod list showed the mod under the mod id `racmc` with the version `0.1.0`), listened on `*:25565` and reported `Done (0.720s)! For help, type "help"`. It was then stopped with the server console command `stop`, which saved the players and the world of every dimension (`Saving chunks for level .../minecraft:overworld`, `.../minecraft:the_nether`, `.../minecraft:the_end`, `All dimensions are saved`) and ended the Gradle task with `BUILD SUCCESSFUL` (exit code 0, 18 s). The final harness that delivered the `stop` input ran with `--no-configuration-cache`; see the note on the server console below. This run was made on the foundation, before the display name of the project was changed. |
| `CI=true gradlew.bat --no-daemon clean build` | `BUILD SUCCESSFUL`. This is the command the Windows CI job runs, with the environment variables that GitHub Actions sets. The first cold run took about 40 s; a run with a warm Gradle build cache took 5 s. |
| `CI=true gradlew.bat --no-daemon test --rerun` | `BUILD SUCCESSFUL`, 11 of 11 tests passed with the test task forced to re-execute on the foundation, before the importer was added. |
| `gradlew.bat test --rerun -Dracmc.isoImage=<path> -Dracmc.isoExpectedRevision=<revision>` | `BUILD SUCCESSFUL`, the opt-in check of the real disc image passed for the European PAL v2.00 release and for the North American NTSC-U v1.00 release. Both images were read in place, were accepted and were identified with the expected revision. The size and the SHA-1 digest of each image matched the values of the Redump record of the disc. The images were not copied into the repository and are not part of it. |
| `gradlew.bat test --rerun -Dracmc.isoImage=<NTSC-U v1.00> -Dracmc.isoExpectedRevision="NTSC-U v1.00"` | `BUILD SUCCESSFUL`, 201 of 201 tests passed, the twelve opt-in checks of the real disc image included: the scenery, the hero and the Blaster are converted with the numbers of the vertices, of the triangles, of the joints, of the sequences and of the textures that were measured on the disc, every vertex of the hero and of the Blaster is used by a triangle, the triangles cover the whole model, the chain of the hand of the hero ends at the joint 56 and the skinning holds the size of the model. |
| `gradlew.bat runClient` with the disc image of the NTSC-U v1.00 release configured | The client window opened (GL 4.6, NVIDIA GeForce RTX 5090). The log shows `The disc image gate keeps the main menu closed until the disc image is verified and the assets are in the cache.`, `The disc image picker was opened and cannot be dismissed.`, `The disc image was verified: NTSC-U v1.00.` and `The cache of the assets is started for the disc image.`. The cache was written (`racm-4fe30dba….racm`, 5,653,069 bytes) and the main menu opened with the button *Blaster test* on it. The screen of the demo of the Blaster was opened and left open for two minutes: the log holds no error and no warning of the mod, so the renderer of the meshes wrote the triangles of the hero, of the Blaster and of the scenery in every frame without the failure of the buffer of the renderer of the game of blocks. A picture of the screen was taken by the game itself (`Saved screenshot as 2026-11-02_21.04.22.png`): it shows the whole textured hero standing in the middle of the screen with the textured Blaster in his hand, the text of the screen under the model and the button under the text. The mouse and the keyboard of the session of the agent do not reach the window of the game, so the picture was taken by the game itself and not by a key of the keyboard; the worker that verifies the tree can take its own picture. |
| `gradlew.bat test --rerun` on the tree of the demo of the Blaster | `BUILD SUCCESSFUL`, 216 tests in all: 195 passed and 21 skipped, the opt-in checks of a real disc image. This is the measurement of the tree of the demo of the Blaster before this revision; the independent worker T03 counted the same 216 tests on the exact commit `88d9453` (195 passed, 21 skipped, no failure and no error). |
| `gradlew.bat test --tests "com.github.giannigr.racmc.client.render.Rac1MeshRendererTest"` on the revision `734b0c3` | `BUILD SUCCESSFUL`, 8 of 8 tests passed: the three checks of the writing of a triangle as a quad of four vertices and the five restored checks of the grouping of the triangles by their material. |
| `gradlew.bat test --rerun` on the revision `734b0c3` | `BUILD SUCCESSFUL`, 221 tests in all: 200 passed, 21 skipped, no failure and no error, counted from the XML reports of the test task. The twenty-one skipped tests are the opt-in checks of a real disc image. |
| `gradlew.bat build` on the revision `734b0c3` | `BUILD SUCCESSFUL`. The test task of the build was up to date and held the same 221 tests with 200 passed and 21 skipped that `gradlew.bat test --rerun` had measured. `gradlew.bat jar --rerun` built `build/libs/racmc-0.1.0.jar` (169,303 bytes) again. |
| `gradlew.bat test --tests "com.github.giannigr.racmc.asset.MobySequenceReaderTest" --tests "com.github.giannigr.racmc.asset.Rac1AnimationEvaluatorTest" --rerun` on this revision | `BUILD SUCCESSFUL`, 20 of 20 tests passed: the eight checks of the reader of the sequences of the game and the twelve checks of the evaluator of the animation. The same command on the tree without the fix of the reader failed in three of them: the key at the end of the sequence held no record of the channels (two records of translations and two of scales were expected, two of each were found), and the pose at the end of the cycle of the sequence was 1.0001502 instead of 1.5. |
| `gradlew.bat test --rerun` on this revision | `BUILD SUCCESSFUL`, 223 tests in all: 202 passed, 21 skipped, no failure and no error, counted from the XML reports of the test task. The twenty-one skipped tests are the opt-in checks of a real disc image. |
| `gradlew.bat clean build` on this revision | `BUILD SUCCESSFUL`, the same 223 tests with 202 passed and 21 skipped after a clean, and `build/libs/racmc-0.1.0.jar` (169,968 bytes) was built again. |
| `gradlew.bat test --rerun -Dracmc.isoImage=<NTSC-U v1.00> -Dracmc.isoExpectedRevision="NTSC-U v1.00"` on the tree of the demo of the Blaster | `BUILD SUCCESSFUL`, 216 of 216 tests passed with the twenty-one opt-in checks of the real disc image included: the joint lists of the hero name the arm of the weapon, the hero faces the first axis, the weapon holds a sequence for the pose it is held in and one for the shot, the third axis of the wrist of the hero points the way the hero faces in the stance of the weapon, and the chain of the muzzle of the Blaster holds seven joints. This is the report of P03; the run was not repeated for this revision, because a run on a real disc image is the scope of the independent worker T03, so the result is not independently verified here. |
| `gradlew.bat clean build` on the tree of the demo of the Blaster | `BUILD SUCCESSFUL`, the same 216 tests in all: 195 passed and 21 skipped after a clean, and `build/libs/racmc-0.1.0.jar` was built again. This is the report of P03 on the tree of the demo of the Blaster before this revision; the clean build of this revision was run again and is named in the rows of this revision. |
| `CI=true .\gradlew.bat --no-daemon build` on the tree of the demo of the Blaster | `BUILD SUCCESSFUL`. This is the command the Windows CI job runs, with the environment variables that GitHub Actions sets. This is the report of P03 on the tree of the demo of the Blaster before this revision. |
| `gradlew.bat runClient` on the tree of the demo of the Blaster | The client window opened, the disc image of the NTSC-U v1.00 release was verified again, the cache of the assets was used, and the screen of the demo of the Blaster was drawn. The pictures of the demo were taken by the game itself (`p03-front-0-t0.1.png` up to `p03-front-20-t2.9.png` under `run/screenshots`): they show the hero facing the player with the Blaster in his right hand, the barrel of the weapon pointing the way the hero faces, the weapon playing the sequence of the shot once every 2.5 seconds and the hero playing his stance of the weapon without a jump. The log holds the moment of the demo, the view of the model and the box around the hero and around the weapon of every picture. The client was stopped afterwards, because the mouse and the keyboard of the session of the agent do not reach the window of the game. This is the report of P03 on the tree of the demo of the Blaster before this revision; the client was not started again for this revision, because a run on a real disc image is the scope of the independent worker T03. |

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
