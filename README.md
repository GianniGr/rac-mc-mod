# Ratchet & Clank: Getting Wood

A community-made Minecraft Java Edition mod project inspired by **Ratchet & Clank**.

## Project status

**Foundation and disc importer started.** The Minecraft mod project is set up and buildable, and the
first milestone (M1) covers the asset import pipeline and the first rendering of original assets.
The local disc importer is now in place up to the point where a validated disc image can be read;
the game keeps the disc image mandatory and asks for it in the main menu. Extracting the assets
themselves and rendering them is not implemented yet.

## Vision

Build a Minecraft experience featuring Ratchet & Clank-inspired gameplay, with a focus on importing
original game assets from a player's legally obtained game disc image rather than distributing
those assets in the mod.

The planned development roadmap includes an asset-import pipeline, weapons and crates, a Gadgetron
vendor, flyable spaceships, enemies, planetary exploration, and eventually spaceflight within the
Solana Galaxy.

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

## Importing your own disc

The mod reads the assets of a Ratchet & Clank disc image that the player owns. The image is never
uploaded, never redistributed and never written to; it is only read.

Two disc revisions are supported:

| Revision | Product code | Video mode |
| --- | --- | --- |
| PAL v2.00 | `SCES_509.16` | PAL (Europe) |
| NTSC-U v1.00 | `SCUS_971.99` | NTSC (North America) |

Every other revision - the PAL v1.00 release, the Japanese release and the demo discs - is refused
with a clear message, and so is a file that is not an ISO 9660 disc image or that uses a container
format such as CHD.

An image is only accepted when it is the unmodified retail disc: the size and the digest of the
whole image have to match the values of the Redump record of the disc, and so do the size and the
digest of its boot executable. A copy that was changed anywhere is refused.

**A disc image is mandatory.** The game cannot be entered before a verified disc image is
configured: the world list, the screen that creates a world and the server list stay closed, and
the game asks for the image in the main menu. The image is remembered by its path, so it is only
asked for once; the file is verified again on every start of the game, and the game asks again when
the file is missing or was changed.

To point the mod at your disc image, press the **Disc image** button in the lower left corner of
the main menu, enter the path of the ISO file of your disc and let the mod check it. The check
reads the image in the background and shows how far it has come, so the game stays usable while it
runs.

The importer was checked against retail disc images of both supported revisions, with the
permission of the project owner; the images themselves are not part of this repository and are
never redistributed with it.

## Building and running

A Java 21 JDK is required; the Gradle wrapper downloads the pinned Gradle distribution itself.

```bash
./gradlew build          # compile the mod and run the test suite (Windows: .\gradlew.bat build)
./gradlew test           # run the JUnit test suite only
./gradlew runClient      # launch the development client
./gradlew runServer      # launch the development dedicated server
```

The ModDevGradle development `runServer` task does not require the Minecraft EULA: the verified
development launch started, ran and shut down again without a `run/eula.txt` file. That applies to
this development task only; a standalone Minecraft dedicated server distribution is a different
case and still requires the EULA to be accepted before it starts. The development run directories
are ignored by Git.

See [`docs/development.md`](docs/development.md) for the full task list, the project layout,
the description of the disc importer and the details of every verified command.

## Workflow

Development coordination and AI worker procedures are maintained separately in [`GianniGr/gianni-multi-ai-workflows`](https://github.com/GianniGr/gianni-multi-ai-workflows), under `projects/rac-mc-mod/`. The setup PR is merged and workflow PR #18 moved the project workflow into that directory.

## Legal

This is an unofficial fan project and is not affiliated with or endorsed by Insomniac Games or Sony Interactive Entertainment. Original game assets are not included in this repository.

## License

The source code of this project is released under the [MIT licence](LICENSE).

The licence covers the source code only. It grants no rights to the original Ratchet & Clank
assets of Insomniac Games or Sony Interactive Entertainment; those assets are not part of this
repository and have to be imported by the player from a disc image of their own.
