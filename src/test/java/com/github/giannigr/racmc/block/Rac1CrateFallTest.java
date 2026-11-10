package com.github.giannigr.racmc.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ModSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the fall of the crates of the game.
 *
 * <p>The crates of the game fall like the sand of the game of blocks, so the fall itself is the
 * fall of the game of blocks: the block of a crate is a falling block of the game of blocks and
 * it does not write a fall of its own.
 *
 * <p>The checks that need a world of the game are read out of the source of the mod, the way the
 * other checks of the mod do it.
 */
class Rac1CrateFallTest {

    /**
     * The source of the block of a crate.
     *
     * @return the text of the source
     */
    private static String blockSource() {
        return ModSource.of("com/github/giannigr/racmc/block/Rac1CrateBlock.java");
    }

    @Test
    @DisplayName("The block of a crate falls like the sand of the game of blocks")
    void theBlockFallsLikeSand() {
        assertTrue(FallingBlock.class.isAssignableFrom(Rac1CrateBlock.class),
                "The block of a crate has to be a falling block of the game of blocks");
        assertTrue(Fallable.class.isAssignableFrom(Rac1CrateBlock.class),
                "The block of a crate has to be a fallable block of the game of blocks");
    }

    @Test
    @DisplayName("The block of a crate has a block entity, so that it is made again when it lands")
    void theBlockHasABlockEntity() {
        assertTrue(EntityBlock.class.isAssignableFrom(Rac1CrateBlock.class),
                "The block of a crate has to make its block entity again when it comes to rest");
    }

    @Test
    @DisplayName("The fall of a crate is the fall of the game of blocks")
    void theFallIsTheFallOfTheGameOfBlocks() {
        String source = blockSource();

        // The game of blocks schedules the fall of a falling block when the block is placed and
        // when one of its neighbours changes. The block of a crate uses those rules and does
        // not write a fall of its own.
        assertFalse(source.contains("scheduleTick"),
                "The block of a crate must not schedule a fall of its own: " + source);
        assertFalse(source.contains("updateShape"),
                "The block of a crate must not write the rule of the neighbours of its own: "
                        + source);
        assertFalse(source.contains("FallingBlockEntity.fall"),
                "The block of a crate must not start a fall of its own: " + source);
    }

    @Test
    @DisplayName("A crate falls when the block under it is free")
    void aCrateFallsWhenTheBlockUnderItIsFree() {
        // The rule of the game of blocks: a falling block falls when the block under it is air,
        // a liquid or a block that can be replaced.
        assertTrue(FallingBlock.isFree(Blocks.AIR.defaultBlockState()),
                "The air has to be free, so that a crate falls into it");
        assertTrue(FallingBlock.isFree(Blocks.WATER.defaultBlockState()),
                "A liquid has to be free, so that a crate falls into it");
        assertFalse(FallingBlock.isFree(Blocks.STONE.defaultBlockState()),
                "The stone must not be free, so that a crate rests on it");
    }

    @Test
    @DisplayName("A falling crate drops nothing")
    void aFallingCrateDropsNothing() {
        String source = blockSource();

        // The falling block of the game of blocks drops an item when it cannot come to rest.
        // The crates of the mod drop nothing, so that drop is switched off. The switch is the
        // flag of the falling block; the block itself still comes to rest.
        assertTrue(source.contains("entity.dropItem = false;"),
                "The fall of a crate must not drop an item: " + source);
        assertFalse(source.contains("disableDrop"),
                "The crate must still come to rest, so the fall must not be cancelled: " + source);
        assertTrue(source.contains("return List.of();"),
                "The block of a crate must not drop anything: " + source);
    }

    @Test
    @DisplayName("The renderer of the falling block draws the crate and nothing else")
    void theRendererDrawsTheCrate() {
        String source = ModSource.of(
                "com/github/giannigr/racmc/client/render/Rac1FallingBlockRenderer.java");

        assertTrue(source.contains("Rac1CrateDrawing.drawAtOrigin("),
                "The renderer of the falling block has to draw the crate of the game: " + source);
        assertTrue(source.contains("this.vanilla.render("),
                "Every other falling block has to be drawn by the game of blocks: " + source);
        assertTrue(source.contains("instanceof Rac1CrateBlock"),
                "The renderer of the falling block has to know a crate of the game: " + source);
    }

    @Test
    @DisplayName("The renderer of the falling block is registered for the falling block of the game")
    void theRendererIsRegistered() {
        String source = ModSource.of(
                "com/github/giannigr/racmc/client/RacMcClientRegistration.java");

        assertTrue(source.contains("EntityType.FALLING_BLOCK"),
                "The renderer of the crate has to be registered for the falling block of the game: "
                        + source);
        assertTrue(source.contains("Rac1FallingBlockRenderer::new"),
                "The renderer of the crate has to be registered for the falling block of the game: "
                        + source);
    }

    @Test
    @DisplayName("The crate of the game is drawn where it stands while it falls")
    void theCrateIsDrawnWhereItStands() {
        String source = ModSource.of(
                "com/github/giannigr/racmc/client/render/Rac1CrateDrawing.java");

        // The origin of the entity that falls is the middle of the ground of the place of the
        // crate, and the origin of the crate of the game is the middle of its own width and at
        // its feet, so the crate is drawn without being moved.
        assertTrue(source.contains("drawAtOrigin("),
                "The crate of the game has to be drawn at the origin of the entity that falls: "
                        + source);
    }
}
