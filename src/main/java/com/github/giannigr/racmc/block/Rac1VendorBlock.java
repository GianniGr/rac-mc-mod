package com.github.giannigr.racmc.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Grounded, visual-only kiosk. No falling physics, shop, use handler or rewards. */
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
                .strength(2.0f, 6.0f).sound(SoundType.METAL);
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
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }
}
