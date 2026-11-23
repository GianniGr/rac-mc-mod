package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.RacMcConstants;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Grounded kiosk with tool-gated pickup. Shop interaction is a separate feature. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1VendorBlock extends BaseEntityBlock {
    public static final MapCodec<Rac1VendorBlock> CODEC = simpleCodec(Rac1VendorBlock::new);
    // Owner's main-body one-block interaction footprint. Antennas/logo never enlarge collision.
    // The 2x interim visual enclosure is approximately one block; see measured bounds in vendor.md.
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public Rac1VendorBlock(Properties properties) {
        super(properties);
    }

    public static Properties vendorProperties() {
        return Properties.of().mapColor(MapColor.METAL).noOcclusion()
                .strength(2.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    @Override
    protected MapCodec<? extends Rac1VendorBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Rac1VendorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, RacMcBlockEntities.VENDOR.get(),
                        Rac1VendorBlockEntity::clientTick) : null;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return canMine(state, player.getMainHandItem()) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        return tool != null && canMine(state, tool) ? super.getDrops(state, params) : List.of();
    }

    private static boolean canMine(BlockState state, ItemStack tool) {
        return tool.is(Items.GOLDEN_PICKAXE)
                || (tool.is(ItemTags.PICKAXES) && tool.isCorrectToolForDrops(state));
    }

    @SubscribeEvent
    public static void allowGoldHarvest(PlayerEvent.HarvestCheck event) {
        // The native ServerPlayerGameMode also checks harvestability before calling playerDestroy.
        // Keep the vanilla tier tags intact and grant only this block/item exception.
        if (event.getTargetBlock().getBlock() instanceof Rac1VendorBlock
                && event.getEntity().getMainHandItem().is(Items.GOLDEN_PICKAXE)) {
            event.setCanHarvest(true);
        }
    }
}
