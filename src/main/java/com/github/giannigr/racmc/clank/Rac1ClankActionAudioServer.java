package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.sound.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Side-effect-free movement observer. Only emits optional presentation after accepted collision. */
final class Rac1ClankActionAudioServer {
    private record Owner(Object world,long epoch,Rac1ClankActionClock clock) {}
    private static final Map<UUID,Owner> OWNERS=new HashMap<>();
    private Rac1ClankActionAudioServer() {}
    static void accepted(ServerPlayer player,Rac1ClankSession session,Rac1ClankMotion.Step step,boolean landed) {
        var id=player.getUUID();
        // Native fall/hazard callbacks can invalidate eligibility during the just-resolved move.
        if(!player.isAlive()||player.hurtTime!=0||!session.equipment().worn()
                ||!Rac1ClankNativeCollision.compatible(player)){forget(id);return;}
        var owner=OWNERS.get(id);
        if(owner==null||owner.world()!=player.level()||owner.epoch()!=session.epoch()) {
            owner=new Owner(player.level(),session.epoch(),new Rac1ClankActionClock());OWNERS.put(id,owner);
        }
        var state=session.motion().state();long tick=player.serverLevel().getGameTime();
        var snapshot=session.snapshot(tick);
        // Glide has no jump gravity contract; its start/transition interrupts this clock.
        if(step.action()==Rac1ClankCapability.Action.GLIDE){owner.clock().abort();return;}
        var local=owner.clock().accepted(new Rac1ClankActionClock.Accepted(session.epoch(),
                snapshot.actionSerial(),session.equipment().selected(),step,
                state.velocity().y(),state.gravity(),landed));
        if(local.isPresent())PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new Rac1ClankActionAudioNetwork.Event(new Rac1ClankActionEvent(id,session.epoch(),
                        snapshot.actionSerial(),tick,step.sourceTime(),local.getAsInt())));
        if(landed)PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new Rac1ClankActionAudioNetwork.Event(new Rac1ClankActionEvent(id,session.epoch(),
                        snapshot.actionSerial(),tick,step.sourceTime(),0))); // Completion fact, NOT a landing cue.
    }
    static void forget(UUID id){var owner=OWNERS.remove(id);if(owner!=null)owner.clock().abort();}
    static void clear(){OWNERS.clear();}
}
