package com.github.giannigr.racmc.gear.mixin;

import com.github.giannigr.racmc.item.Rac1ClankItem;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve move-only server-issued stacks at native Creative cloning boundaries. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ClankCreativeSlotMixin {
    @Shadow public ServerPlayer player;
    private static final String SERVER_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
            + "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
            + "Lnet/minecraft/server/level/ServerLevel;)V";

    @Inject(method = "handleSetCreativeModeSlot",
            at = @At(value = "INVOKE", target = SERVER_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void racmc$protectCreativeAssignment(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (!player.isCreative()) return;
        int slot = packet.slotNum();
        if (Rac1ClankItem.protectedStack(packet.itemStack())
                || (slot >= 1 && slot <= 45
                    && Rac1ClankItem.protectedStack(player.inventoryMenu.getSlot(slot).getItem()))) {
            // Creative's client-authored assignment is not a server-confirmed move. Reconcile
            // both injection and attempted overwrite instead of accepting either half of a move.
            player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick",
            at = @At(value = "INVOKE", target = SERVER_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void racmc$protectNativeClone(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        var menu = player.containerMenu;
        if (!player.isCreative() || menu.containerId != packet.getContainerId()) return;
        int slot = packet.getSlotNum();
        boolean middle = packet.getClickType() == ClickType.CLONE && slot >= 0 && slot < menu.slots.size()
                && Rac1ClankItem.protectedStack(menu.getSlot(slot).getItem());
        boolean drag = packet.getClickType() == ClickType.QUICK_CRAFT
                && AbstractContainerMenu.getQuickcraftType(packet.getButtonNum()) == 2
                && Rac1ClankItem.protectedStack(menu.getCarried());
        if (middle || drag) {
            menu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
