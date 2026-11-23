package com.github.giannigr.racmc.loot;

import com.github.giannigr.racmc.item.weapon.Rac1AmmoPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/** World pickups, not owner-bound delivery. Attribution only chooses the stock type. */
final class Rac1AmmoCrateRouting {
    record Stock(Rac1AmmoPolicy policy, int count) {}
    private Rac1AmmoCrateRouting() {}

    static Rac1AmmoPolicy forCause(ServerLevel level, @Nullable Entity cause) {
        Entity owner = cause instanceof Projectile projectile ? projectile.getOwner() : cause;
        if (!(owner instanceof ServerPlayer player) || player.serverLevel() != level || !player.isAlive()
                || player.isRemoved() || player.isSpectator() || player.hasDisconnected()
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player)
            return Rac1AmmoPolicy.BLASTER;
        var inventory = player.getInventory();
        List<Stock> owned = new ArrayList<>();
        for (var policy : Rac1AmmoPolicy.values())
            if (policy.ammo().isPresent() && policy.owns(inventory)) owned.add(new Stock(policy, policy.count(inventory)));
        return choose(owned, level.random::nextInt);
    }

    static Rac1AmmoPolicy choose(List<Stock> owned, IntUnaryOperator draw) {
        var underCap = owned.stream().filter(s -> s.count() < s.policy().cap()).toList();
        var candidates = underCap.isEmpty() ? owned : underCap;
        if (candidates.isEmpty()) return Rac1AmmoPolicy.BLASTER;
        return candidates.get(candidates.size() == 1 ? 0 : draw.applyAsInt(candidates.size())).policy();
    }
}
