package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.RacMcConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import com.github.giannigr.racmc.actions.WrenchActions;

/** Protection only: never destroy a block from the pre-validation left-click event. */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID)
public final class Rac1WrenchEvents {
    public static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("racmc", "wrench_protected"));

    private Rac1WrenchEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void normalStart(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !holdingWrench(player)
                || WrenchActions.deliveringContact(player)) return;
        if (!WrenchActions.normalAttackAllowed(player)
                || player.getAttackStrengthScale(0) < 0.999F) {
            event.setCanceled(true);
        } else {
            WrenchActions.normalAttackAccepted(player);
        }
    }

    public static boolean holdingWrench(Player player) {
        return player.getMainHandItem().getItem() instanceof Rac1WrenchItem;
    }

    public static Rac1WrenchBlockPolicy.Action action(BlockState state) {
        return Rac1WrenchBlockPolicy.action(BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .toString(), state.is(PROTECTED));
    }

    private static boolean denied(BlockState state) {
        var action = action(state);
        return action == Rac1WrenchBlockPolicy.Action.PROTECTED
                || action == Rac1WrenchBlockPolicy.Action.EXPLOSIVE_ADAPTER_REQUIRED;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!holdingWrench(event.getEntity())
                || !event.getLevel().hasChunkAt(event.getPos())) {
            return;
        }
        if (denied(event.getLevel().getBlockState(event.getPos()))) {
            // Actual cancellation also blocks creative and forged STOP packets.
            event.setCanceled(true);
            resync(event.getEntity(), event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (holdingWrench(event.getPlayer()) && denied(event.getState())) {
            event.setCanceled(true);
            resync(event.getPlayer(), event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCritical(CriticalHitEvent event) {
        applyCriticalPolicy(event.getEntity().getMainHandItem(), event);
    }

    static void applyCriticalPolicy(net.minecraft.world.item.ItemStack mainHand,
                                    CriticalHitEvent event) {
        if (mainHand.getItem() instanceof Rac1WrenchItem) {
            event.setCriticalHit(false);
            event.setDamageMultiplier(1.0f);
        }
    }

    private static void resync(Player player, BlockPos pos) {
        if (player instanceof ServerPlayer server && server.level().hasChunkAt(pos)
                && server.canInteractWithBlock(pos, 1.0)) {
            server.connection.send(new ClientboundBlockUpdatePacket(server.level(), pos));
            var entity = server.level().getBlockEntity(pos);
            if (entity != null && entity.getUpdatePacket() != null) {
                server.connection.send(entity.getUpdatePacket());
            }
        }
    }
}
