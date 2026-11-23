package com.github.giannigr.racmc.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

/** Bounded ephemeral projection of validated canonical owner bindings, not another session service. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID)
public final class Rac1ShopObserverServer {
    private static final class Entry {
        final Rac1ShopMotion motion=new Rac1ShopMotion();
        final UUID incarnation=UUID.randomUUID();
        Rac1ShopObserverNetwork.State state;
        long changes=-1;
    }
    private static final class World {
        final Map<Rac1VendorBlockEntity,Entry> vendors=new WeakHashMap<>();
        long revision;
    }
    private static final Map<MinecraftServer,World> worlds=new WeakHashMap<>();
    private Rac1ShopObserverServer() {}
    static void update(MinecraftServer server,Collection<Rac1VendorBlockEntity> active) {
        World world=worlds.computeIfAbsent(server,key -> new World());
        for (var vendor:active) if (world.vendors.size()<4096 || world.vendors.containsKey(vendor))
            world.vendors.computeIfAbsent(vendor,key -> new Entry());
        Set<Rac1VendorBlockEntity> occupied=Collections.newSetFromMap(new IdentityHashMap<>());
        occupied.addAll(active);
        long tick=server.overworld().getGameTime();
        world.vendors.entrySet().removeIf(pair -> {
            var vendor=pair.getKey(); var entry=pair.getValue();
            if (!(vendor.getLevel() instanceof ServerLevel level) || vendor.isRemoved()
                    || !level.hasChunkAt(vendor.getBlockPos()) || level.getBlockEntity(vendor.getBlockPos())!=vendor) return true;
            var stamp=entry.motion.update(occupied.contains(vendor),tick*3);
            if (stamp.changes()!=entry.changes) {
                entry.changes=stamp.changes();
                entry.state=new Rac1ShopObserverNetwork.State(level.dimension().location(),vendor.getBlockPos(),
                        entry.incarnation,++world.revision,stamp.phase(),stamp.startFrame(),tick);
                // First open may precede a new entry's chunk-Sent observation; use actual chunk trackers.
                for (var player:level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(vendor.getBlockPos()),false)) {
                    Rac1ShopObserverNetwork.send(player,entry.state);
                }
            }
            return stamp.phase()==Rac1ShopMotion.Phase.CLOSED;
        });
    }
    @SubscribeEvent public static void sent(ChunkWatchEvent.Sent event) {
        var world=worlds.get(event.getLevel().getServer());
        if (world==null || event.getPlayer().serverLevel()!=event.getLevel()) return;
        for (var pair:world.vendors.entrySet()) {
            var vendor=pair.getKey(); var entry=pair.getValue();
            if (vendor.getLevel()==event.getLevel() && new net.minecraft.world.level.ChunkPos(vendor.getBlockPos()).equals(event.getPos())
                    && entry.state!=null) {
                var state=entry.state;
                Rac1ShopObserverNetwork.send(event.getPlayer(),new Rac1ShopObserverNetwork.State(state.dimension(),state.position(),
                        state.incarnation(),state.revision(),state.phase(),state.startFrame(),event.getLevel().getServer().overworld().getGameTime()));
            }
        }
    }
    /** Current public projection only; an absent/retired entry means baseline idle. */
    static Optional<Rac1ShopObserverNetwork.State> snapshot(Rac1VendorBlockEntity vendor) {
        if (!(vendor.getLevel() instanceof ServerLevel level)) return Optional.empty();
        var world=worlds.get(level.getServer());
        var entry=world==null ? null:world.vendors.get(vendor);
        return Optional.ofNullable(entry==null ? null:entry.state);
    }
    static void clear(MinecraftServer server) { worlds.remove(server); }
}
