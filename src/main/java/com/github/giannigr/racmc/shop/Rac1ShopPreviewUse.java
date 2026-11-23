package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Accept the vanilla server use-on-block and issue a private shop session.
 * Held items cannot place/use behind the menu; no client imports or server container.
 */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID)
public final class Rac1ShopPreviewUse {
    private Rac1ShopPreviewUse() {}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !event.getLevel().hasChunkAt(event.getPos())
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof Rac1VendorBlockEntity)) return;
        event.setCanceled(true);
        boolean valid=event.getEntity().isAlive() && !event.getEntity().isSpectator()
                && event.getEntity().canInteractWithBlock(event.getPos(),0);
        event.setCancellationResult(valid ? InteractionResult.SUCCESS:InteractionResult.FAIL);
        if (valid && event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            Rac1ShopTransactionServer.get(player.server).open(player,event.getPos()).ifPresent(opened -> Rac1ShopNetwork.opened(player,opened));
    }
}
