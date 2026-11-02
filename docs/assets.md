# The conversion of the assets and the cache

This document describes how the mod turns the data of a Ratchet & Clank disc image into the
geometry the game of blocks uses, how the result is kept in a local cache, and how the game is kept
closed until that cache holds both assets.

## What is converted

The mod converts two assets of the first level of the game (the level of Novalis, the level number
1 of the table of contents of the disc):

| Asset | Class | What it is |
| --- | --- | --- |
| `tie_452` | 452 | A piece of the scenery of the level. The class holds three levels of detail; the converter takes the one with the highest detail. |
| `moby_0` | 0 | The hero of the game. The class holds the mesh, the skeleton and the animation sequences of the hero. |

Both assets carry their textures. The scenery uses one texture, the hero uses four.

The class numbers were found by reading the disc image of the player; they are not guesses. The
class 452 of Novalis is placed 146 times in the level, and the hero class 0 carries 111 joints and
88 animation sequences.

## How the data is found

The game does not use the file system of the disc for its data. It reads everything by absolute
sector, so the converter needs the same access:

1. The table of contents at sector 1500 names the level table and the header of every level.
2. The header of a level names the container of the data of the level.
3. The container holds the core index, the image of the memory of the graphics chip and the
   compressed core data of the level.
4. The core index names the tables of the classes, the tables of the textures and the base of the
   pixels. It also holds the 256 offsets of the animation sequences of the hero, because the hero
   has no sequences of his own.
5. The core data is one compressed stream. The decoder of the game (`WAD`) is implemented in
   `com.github.giannigr.racmc.wad`.

Every step checks the sizes it read, so a damaged disc is reported instead of being turned into
half an asset.

## The geometry

The geometry of the game is Z-up, the geometry of the game of blocks is Y-up. The converter keeps
the game Z-up while it decodes and while it skins the mesh, and turns the axes around at the end:

```
game      x  y  z        blocks     x  z  -y
```

The scale of a class is applied exactly once: to the positions of the vertices and to the places of
the joints of the skeleton. The unit of the game is about a metre - the hero is 1.42 units tall -
and a block of the game of blocks is a metre as well, so one unit of the game is one block.

The scenery is read out of the class of the tie: the packet tables of the level of detail with the
highest detail, the strips of every packet and the vertices. The vertices of a packet are not stored
in the order in which they are drawn, they name the place in the graphics packet of the console
where they are written. The reader walks that packet, exactly like the program of the console does.

The hero is read out of the class of the moby: the mesh is a number of packets, each of them a
command list for the vector unit of the console with the texture coordinates of the vertices, the
indices of the triangles and the material blocks. The vertices do not name their joints directly,
they name the places of the memory of the vector unit in which the matrices of the joints are
stored, so the reader replays the machine of the console to find out which joints a vertex follows
and how much they weigh.

The skeleton holds one inverse bind matrix and one parent for every joint. The animation sequences
hold a quaternion for every joint of every key, and the translations and the scales of the joints
that differ from the rest pose.

## The sources of the format knowledge

The format of the data was not guessed. The following public projects measured it:

| Source | Licence | What was taken from it |
| --- | --- | --- |
| [ReRAC](https://github.com/re-rac/rerac) (`docs/formats/*.md`, `crates/rc-formats`) | ISC | The layouts of the disc tables, the compressed streams, the classes of the scenery and of the characters, the animation format and the rules of the evaluator of the animation |
| [OpenRAC](https://github.com/OpenRAC/OpenRAC) (`docs/engine/formats/*.md`) | GPL-3.0 for the engine, MIT for `editor/` | The description of the tables of the disc and the confirmation of the measurements on the disc of the game |

Only the facts of the formats were used. No code of the GPL parts was copied, no dependency was
added, and the implementation in this repository is independent. The attribution of the ReRAC
project (ISC, Copyright (c) 2026 ReRAC contributors) and of the OpenRAC project are due here.

The licence of the project itself (MIT) covers the source code of this repository only. It grants
no rights to the assets of the game, which are never part of this repository.

## The cache

The converted assets are kept in a local cache file. The file is written by the player, into the
cache directory of the mod, and it is never part of the repository.

The file starts with the magic `RACM`, holds a header of 64 bytes, a table that names the sections
and the sections themselves. Every integer is stored in the little endian order.

| Offset | Size | Field |
| --- | --- | --- |
| 0x00 | 4 | the magic `RACM` |
| 0x04 | 2 | the version of the format |
| 0x06 | 2 | the size of the header |
| 0x08 | 4 | the version of the schema of the assets |
| 0x0C | 4 | the flags of the file, zero |
| 0x10 | 4 | the number of the bytes of the identity of the disc |
| 0x14 | 4 | the number of the sections |
| 0x18 | 4 | the offset of the table of the sections |
| 0x1C | 4 | the length of the table of the sections |
| 0x20 | 8 | the length of the payload |
| 0x28 | 4 | the checksum of the payload |
| 0x2C | 4 | the checksum of the header |
| 0x30 | 16 | reserved, zero |

The identity of the disc is the name of the release, the product code of the disc and the digest of
the whole disc image. All three are public facts of the retail releases, so the cache says which
disc it belongs to without giving away anything about the file of the player.

A cache is only used when all of the following agree:

* the magic and the version of the format,
* the checksum of the header,
* the version of the schema of the assets,
* the identity of the disc,
* the checksum of the payload,
* the checksum of every one of the sections, and
* both assets can be read out of the file.

A cache that fails one of these checks is thrown away and built again. A cache that is being built
is never used, so the game never works with assets that are missing or only half there.

## The life of the cache

1. The player picks a disc image in the setup screen. The image is verified against the digests of
   its release.
2. As soon as the image is verified, the cache of that disc is looked for.
3. When the cache holds both assets, it is used and the game may be entered.
4. When there is no cache, the assets are converted in the background. The setup screen shows the
   step and the share of the work that is done.
5. When the conversion fails, the setup screen shows what went wrong and offers to try again. The
   game stays closed.

A new cache is written into a staging file, read back and checked, and only then moved onto the
file of the cache. A game that is stopped while the cache is written therefore never sees a cache
that holds only half of the assets.

## The preview

While the setup screen is open and the cache is ready, the screen draws both assets: the piece of
scenery turns slowly and the hero walks through his animation. The preview uses the same renderer
as the game would use, so it shows what was converted.

## Known limits

* Only the two assets of the first level are converted. The converter is written so that more
  classes can be added, but the mod does not use them yet.
* The animation of the hero is evaluated on the processor of the game, without the pose layers and
  the joint modifiers that the game builds while it runs. The parts of the mesh that follow the two
  joints whose bind matrices are not a pure turn are moved further than the rest of the mesh.
* The cache holds the assets of the first level only. A cache of another level would need another
  schema version.
