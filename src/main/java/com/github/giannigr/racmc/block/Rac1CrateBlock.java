package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.asset.Rac1CrateDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * A crate of the game as a block of the game of blocks.
 *
 * <p>The block holds no geometry of its own: the crate of the game is drawn by the renderer of
 * the block entity, out of the mesh that was imported out of the disc. The block itself is
 * invisible and holds the space of one block.
 *
 * <p>No inventory-item loot. Genuine player/explosion destruction goes through the shared reward
 * service; wooden and explosive crates emit currency. Explosives also use the authoritative
 * fuse/blast service. Health/ammo rewards are not implemented.
 *
 * <p>The block falls like the sand of the game of blocks: when the block below it is gone, it
 * falls down as a falling block and it comes to rest on the first block under it. The falling
 * block of the game of blocks is used for that, so the fall of a crate is the fall of the sand of
 * the game of blocks. A stack of crates falls one after the other, because every crate that
 * lands schedules the fall of the crate above it.
 *
 * <p>The falling block of the game of blocks drops an item when it cannot come to rest. The
 * crates of the mod drop nothing, so that drop is switched off.
 */
public class Rac1CrateBlock extends FallingBlock implements EntityBlock {

    /** The codec of the description of a crate. */
    private static final MapCodec<Rac1CrateDefinition> DEFINITION_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("key").forGetter(Rac1CrateDefinition::key),
                    Codec.INT.fieldOf("class_number").forGetter(Rac1CrateDefinition::classNumber))
                    .apply(instance, Rac1CrateDefinition::new));

    /** The codec of the block of a crate. */
    public static final MapCodec<Rac1CrateBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(DEFINITION_CODEC.fieldOf("crate")
                    .forGetter(Rac1CrateBlock::definition)).apply(instance, Rac1CrateBlock::new));

    /** The description of the crate the block shows. */
    private final Rac1CrateDefinition definition;
    private net.minecraft.nbt.CompoundTag fallingRewardData;

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                        BlockPos pos, net.minecraft.util.RandomSource random) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Rac1CrateBlockEntity crate) crate.initializeReward();
        fallingRewardData = entity == null ? null : entity.saveWithoutMetadata(level.registryAccess());
        try {
            if (!explosive()) {
                super.tick(state, level, pos, random);
            } else if (isFree(level.getBlockState(pos.below()))
                    && pos.getY() >= level.getMinBuildHeight()) {
                Rac1FallingExplosive.fall(level, pos, state);
            }
        } finally {
            fallingRewardData = null;
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player, boolean willHarvest,
            net.minecraft.world.level.material.FluidState fluid) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            com.github.giannigr.racmc.loot.Rac1CrateRewards.destroy(server, pos, state, player);
            return level.getBlockState(pos) != state;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void onBlockExploded(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            if (explosive()) {
                if (level.getBlockEntity(pos) instanceof Rac1CrateBlockEntity crate) crate.arm(true);
            } else {
                com.github.giannigr.racmc.loot.Rac1CrateRewards.destroy(server, pos, state,
                        explosion.getDirectSourceEntity());
            }
        }
    }

    /**
     * Creates the block of a crate.
     *
     * @param definition the description of the crate the block shows
     */
    public Rac1CrateBlock(Rac1CrateDefinition definition) {
        this(definition, properties(definition));
    }

    /**
     * Creates the block of a crate.
     *
     * @param definition the description of the crate the block shows
     * @param properties the properties of the block
     */
    public Rac1CrateBlock(Rac1CrateDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    /**
     * The description of the crate the block shows.
     *
     * @return the description
     */
    public Rac1CrateDefinition definition() {
        return definition;
    }

    public boolean explosive() {
        return definition.classNumber() == 505;
    }

    @Override
    protected void onExplosionHit(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, net.minecraft.world.level.Explosion explosion,
            java.util.function.BiConsumer<ItemStack, BlockPos> drops) {
        if (explosive()
                && explosion.getBlockInteraction()
                != net.minecraft.world.level.Explosion.BlockInteraction.TRIGGER_BLOCK) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Rac1CrateBlockEntity crate) {
                crate.arm(true);
            }
            // Also retain the block during client replay of vanilla explosion packets.
            return;
        }
        super.onExplosionHit(state, level, pos, explosion, drops);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
            getTicker(net.minecraft.world.level.Level level, BlockState state,
                    net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != RacMcBlockEntities.CRATE.get()) {
            return null;
        }
        return (world, pos, current, entity) ->
                Rac1CrateBlockEntity.serverTick(world, pos, current, (Rac1CrateBlockEntity) entity);
    }

    @Override
    public void onLand(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            BlockState replaced, FallingBlockEntity entity) {
        super.onLand(level, pos, state, replaced, entity);
        if (!level.isClientSide && entity instanceof Rac1FallingExplosive explosive) {
            explosive.landed(pos);
        }
    }

    @Override
    protected MapCodec<? extends Rac1CrateBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // The crate of the game is drawn by the renderer of the block entity, so the block
        // itself draws nothing.
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Rac1CrateBlockEntity(pos, state);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // Currency is emitted by the reward service, never by vanilla item loot.
        return List.of();
    }

    @Override
    protected void falling(FallingBlockEntity entity) {
        // The falling block of the game of blocks drops an item when it cannot come to rest.
        // The crates of the mod drop nothing, so that drop is switched off. The crate itself is
        // still placed when it comes to rest, and it still comes to rest on the first block
        // under it.
        entity.dropItem = false;
        // Vanilla transfers blockData back into the landed block entity.
        if (fallingRewardData != null) entity.blockData = fallingRewardData.copy();
    }

    @Override
    public int getDustColor(BlockState state, net.minecraft.world.level.BlockGetter level,
                            BlockPos pos) {
        // The dust of a falling crate has the colour of the crate on the map of the game.
        return 0xFF000000 | color(this.definition).col;
    }

    /**
     * The properties of the block of a crate.
     *
     * <p>The crate of the game fills its block, so the block of the crate is a solid block. The
     * block does not hide the faces of the blocks around it, because it draws nothing itself:
     * the crate of the game is drawn by the renderer of the block entity.
     *
     * <p>The hardness and the sound of the block are the ones of the planks of the game of
     * blocks: a crate is broken with a bare hand in as long as a plank is, and it sounds like
     * wood. The block does not ask for the right tool, so a bare hand breaks it in the time of
     * a plank of the game of blocks.
     *
     * @param definition the description of the crate
     * @return the properties of the block
     */
    public static Properties properties(Rac1CrateDefinition definition) {
        return Properties.of()
                .mapColor(color(definition))
                .noOcclusion()
                .strength(2.0F, 3.0F)
                .sound(SoundType.WOOD);
    }

    /**
     * The colour of a crate on the map of the game of blocks.
     *
     * @param definition the description of the crate
     * @return the colour
     */
    public static MapColor color(Rac1CrateDefinition definition) {
        return switch (definition.key()) {
            case "health_crate" -> MapColor.COLOR_BLUE;
            case "ammo_crate" -> MapColor.COLOR_GRAY;
            case "explosive_crate" -> MapColor.COLOR_RED;
            default -> MapColor.COLOR_BROWN;
        };
    }
}
