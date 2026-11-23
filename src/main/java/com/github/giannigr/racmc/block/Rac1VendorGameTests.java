package com.github.giannigr.racmc.block;

import com.github.giannigr.racmc.item.RacMcItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("racmc")
@PrefixGameTestTemplate(false)
public final class Rac1VendorGameTests {
    private Rac1VendorGameTests() {}

    @GameTest(template = "empty")
    public static void vendorRequiresValidPickaxeAndDropsItself(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var state = RacMcBlocks.VENDOR.get().defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (var tool : List.of(ItemStack.EMPTY, new ItemStack(Items.WOODEN_PICKAXE),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.DIAMOND_AXE),
                new ItemStack(RacMcItems.OMNIWRENCH.get()), new ItemStack(RacMcItems.BLASTER.get()))) {
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            helper.assertTrue(state.getDestroyProgress(player, level, pos) == 0, "Wrong tool can mine the kiosk");
            helper.assertTrue(!state.canHarvestBlock(level, pos, player), "Wrong tool passed native harvest");
            helper.assertTrue(Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool).isEmpty(),
                    "Wrong tool received the kiosk");
        }
        for (var item : List.of(Items.GOLDEN_PICKAXE, Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            var tool = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            helper.assertTrue(state.getDestroyProgress(player, level, pos) > 0, "Valid pickaxe cannot mine the kiosk");
            helper.assertTrue(state.canHarvestBlock(level, pos, player), "Valid pickaxe failed native harvest");
            var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getCount() == 1
                    && drops.getFirst().is(RacMcItems.VENDOR.get()), "Expected one placeable kiosk");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goldenPickaxeHarvestsOneVendorThroughNativeBreak(GameTestHelper helper) {
        var level = helper.getLevel();
        var local = new BlockPos(3, 2, 3);
        var pos = helper.absolutePos(local);
        var vendor = RacMcBlocks.VENDOR.get().defaultBlockState();
        level.setBlock(pos, vendor, Block.UPDATE_ALL);
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "racmc-vendor-test"), ClientInformation.createDefault());
        var gold = new ItemStack(Items.GOLDEN_PICKAXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, gold);
        helper.assertTrue(!Blocks.IRON_ORE.defaultBlockState().canHarvestBlock(level, pos, player),
                "Gold vendor exception leaked into vanilla iron ore");
        helper.assertTrue(player.gameMode.destroyBlock(pos) && level.isEmptyBlock(pos),
                "Native survival mining did not break the kiosk");
        helper.assertItemEntityCountIs(RacMcItems.VENDOR.get(), local, 1, 1);
        helper.succeed();
    }
}
