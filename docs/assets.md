# The conversion of the assets and the cache

This document describes how the mod turns the data of a Ratchet & Clank disc image into the
geometry the game of blocks uses, how the result is kept in a local cache, and how the game is kept
closed until that cache holds the assets.

## What is converted

The mod converts three assets of the first level of the game (the level of Novalis, the level number
1 of the table of contents of the disc):

| Asset | Class | What it is |
| --- | --- | --- |
| `tie_452` | 452 | A piece of the scenery of the level. The class holds three levels of detail; the converter takes the one with the highest detail. |
| `moby_0` | 0 | The hero of the game. The class holds the mesh, the skeleton and the animation sequences of the hero. |
| `gadget_168` | 168 | The Blaster, the weapon of the hero. It is a gadget, so its class is a compressed stream of its own. |

All three classes carry their textures. The scenery uses one texture, the hero uses four and the
Blaster uses one.

The class numbers were found by reading the disc image of the player; they are not guesses. The
class 452 of Novalis is placed 146 times in the level, and the hero class 0 carries 111 joints and
88 animation sequences. The Blaster is the item 15 of the hand slot of the hero and it has the
class 168; the ReRAC project measured the list of the gadgets and the names of the items in the
code of the game, see `docs/plan/gadgets.md` of that project.

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

The vertices of the packets of a class are collected in one list, but the indices of a packet name
the vertices of that packet alone. The reader therefore moves the indices of every packet by the
number of the vertices of the packets that came before it, and it refuses a mesh whose triangles
name a vertex that does not exist.

The Blaster is a gadget of the game. The gadgets - the weapons and the tools of the hero - are
not part of the core data like the other classes: every gadget is a compressed stream of its own
and the table of the gadgets of the level (the core index at `+0x80` and `+0x84`) names the stream
of every gadget together with the number of its class. The stream is decoded and the result is
the same class blob as the one of the other classes, so the same reader is used for it. The
textures of a gadget are named by the entry of the class table of the level, which holds the
textures but no geometry for the gadgets.

The skeleton holds one inverse bind matrix and one parent for every joint. The animation sequences
hold a quaternion for every joint of every key, and the translations and the scales of the joints
that differ from the rest pose.

The game of the disc keeps the rows of its matrices, and the row 0 of a matrix is the image of its
first axis. The converter reads the rows of the skeleton of the disc the other way around, builds
the local matrix of a joint out of the quaternion of its key and multiplies the chain of the
joints the way the game does: the pose of a joint is the pose of its parent times the local
matrix of the joint, and the matrix that moves the vertices is that pose times the inverse bind
matrix of the joint. The checks of the test suite hold the conventions of the game, and the opt-in
check of a real disc image holds the size of the hero and the size of the Blaster against the
numbers that were measured on the disc.

One joint of the Blaster has a bind pose that collapsed to a point, so its inverse bind matrix
has an enormous scale that the game cannot undo with the scales of its animations. The converter
recognises such a matrix and uses the rest pose of the joint instead, so that the eight vertices
that follow the joint keep the place the model gave them.

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

The file holds three sections: the scenery, the hero and the Blaster. Every section holds the
number of the class and the name of the asset, so that the cache says which asset it holds.

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
* all of the assets can be read out of the file.

A cache that fails one of these checks is thrown away and built again. A cache that is being built
is never used, so the game never works with assets that are missing or only half there.

## The life of the cache

1. The player picks a disc image in the setup screen. The image is verified against the digests of
   its release.
2. As soon as the image is verified, the cache of that disc is looked for.
3. When the cache holds all of the assets, it is used and the game may be entered.
4. When there is no cache, the assets are converted in the background. The setup screen shows the
   step and the share of the work that is done.
5. When the conversion fails, the setup screen shows what went wrong and offers to try again. The
   game stays closed.

A new cache is written into a staging file, read back and checked, and only then moved onto the
file of the cache. A game that is stopped while the cache is written therefore never sees a cache
that holds only half of the assets.

## The demo of the Blaster

The mod shows the imported assets in the game: the hero holds his Blaster and fires it.

* The hero is posed with one of his own imported animation sequences. The demo uses the stance of
  the hero with the weapon in his hand, the sequence 56: the ReRAC project measured it in the
  definitions of the items of the game, where the Blaster names the sequence 56 as the stance of
  the hero, the sequence 57 as the layer of his arm and the sequence 62 as the layer of his arm
  while he crouches. The idle of the hero is the fallback, and so is the first sequence of the
  class, when the hero of the disc does not hold the stance.
* A sequence that repeats itself repeats itself over its last key. The reader of the game adds a
  key at the end of such a sequence, and that key holds the pose of the first key, so the frame
  after the last one is the first one again. The evaluator of the mod takes the time back into the
  cycle of the sequence at that point, so the pose of the hero is the same on both sides of the
  end of the sequence and the hero does not jump. A cycle that was made longer would hold the
  pose of the first key for a whole key interval at its end, and the hero would stand still once
  per cycle.
* The Blaster is posed with its own imported sequences. The weapon is held with the sequence 1,
  which holds a single key, and it fires with the sequence 4: the ReRAC project measured in the
  code of the game that the weapon blends to its sequence 4 when it fires and back to its
  sequence 1 when it is held. The shot is played once every 2.5 seconds, over the length of the
  sequence of the shot itself, and the weapon is blended into the shot and out of it again over
  the first interval of that sequence.
* The Blaster hangs on the joint of the skeleton of the hero that the game hangs the items of the
  hand on. That joint is read out of the class of the hero: the first list of its joint lists is
  the chain of the hand, and it ends at the joint 56, the joint of the OmniWrench. The converter
  reads the chain instead of assuming the number of the joint. The chain of the hand holds the
  joints 52 to 56, the arm of the weapon, and the twelfth list of the class - the list that the
  game evaluates for the layer of the arm of the hero - ends at the joint 52, the shoulder of the
  same arm. The hero faces the first axis - the toes of his feet point along it - and stands
  upright, so the side of the third axis that the arm of the weapon reaches is his right side.
  The weapon of the hero is therefore in his right hand, and the check of a real disc image holds
  these numbers.

The game places the items of the hand with the pose of the joint they hang on: the item brings
the place of its grip with it, so the game adds no matrix of its own - the origin of the model of
the item sits at the joint. The pose of the joint is the pose of its chain, without the scales
that only reach the joint itself, because the game leaves those scales out of the place of the
items of the hand. The demo does the same, so the Blaster sits in the hand of the hero the way
the game draws it: in the stance of the weapon the third axis of the wrist of the hero points the
way the hero faces, and the Blaster is 0.74 units long along that axis.

The demo is reachable in three ways:

* the setup screen, which the client shows when it starts, draws the hero with the Blaster as long
  as the cache holds the assets;
* the main menu has a **Blaster test** button that opens the demo screen;
* the key **B** opens the demo screen while a world is played.

The screen measures the model and draws it in the middle of the area above the text, so that the
whole hero and the whole weapon are visible and the text never covers them. The box around the
model is measured at the keys of the sequences of the hero and of the Blaster, over the whole
cycle of the sequence of the hero and over the whole firing sequence of the Blaster, so that the
model stands still on the screen while the demo runs. A box that is measured in every frame would
move the model on the screen, because the Blaster moves in the hand of the hero while it fires.

## The preview

While the setup screen is open and the cache is ready, the screen draws the assets: the hero holds
his Blaster and fires it, and the piece of scenery stands next to them. The preview uses the same
renderer as the game would use, so it shows what was converted.

The triangles of a mesh follow each other in the order of the packets of the class of the game, and
the materials of the packets follow each other again and again. The renderer of the game of blocks
holds one buffer for every kind of triangle and closes a buffer as soon as another kind of
triangle is written to it, so the renderer of the mod groups the triangles by their material and
writes every kind of triangle in one run.

The kinds of triangles of the game of blocks are drawn in quads and not in triangles: they take
four vertices for every triangle and cut them into two triangles. The renderer of the mod writes
every triangle of the mesh as a quad of four vertices, whose first vertex is written a second
time. A triangle that is written as a list of three vertices only would be cut in two by the
renderer of the game of blocks, and the second triangle of the quad would join the last vertex of
that triangle with the first vertex of the next triangle of the mesh: the model would be covered
with thin shards.

## Known limits

* Only the assets of the first level are converted: one piece of scenery, the hero and his
  Blaster. The converter is written so that more classes can be added, but the mod does not use
  them yet.
* The animation of the hero is evaluated on the processor of the game, without the pose layers and
  the joint modifiers that the game builds while it runs. The pose of the hero is therefore the
  pose of the disc and not the pose the game builds out of it.
* The items of the hand are placed with the pose of the joint of the hand and with no matrix of
  their own, the way the game places them: the item brings the place of its grip with it. The
  attach word of every item - the joint of the hero that an item hangs on - is in the code of the
  game and not in the data of the disc, so the demo uses the joint of the hand of the hero, the
  joint 56, for the Blaster.
* The cache holds the assets of the first level only. A cache of another level would need another
  schema version.
