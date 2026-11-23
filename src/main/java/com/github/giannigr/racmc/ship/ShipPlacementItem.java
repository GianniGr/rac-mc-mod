package com.github.giannigr.racmc.ship;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Creative fixture only; acquisition and reclaim rules intentionally absent. */
final class ShipPlacementItem extends Item {
    ShipPlacementItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if (player == null || !player.isCreative() || player.isPassenger()
                || !level.dimension().equals(Level.OVERWORLD)) {
            return fail(player, "place_mode");
        }
        if (context.getClickedFace() != Direction.UP) return fail(player, "place_pad");
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer placer)
                || !placer.canInteractWithBlock(context.getClickedPos(), 0)) return InteractionResult.FAIL;

        var ship = new Rac1TravelShip(Rac1Ships.SHIP.get(), server);
        var clicked = context.getClickedPos();
        var base = clicked;
        if (ship.hasRefitCockpit()) {
            // The square 10.4-wide envelope otherwise has no sensible ground-level center click.
            // Click the near pad edge; require independent permission and support at the resulting center.
            var displacement = Vec3.atCenterOf(clicked).subtract(placer.position());
            var inward = Math.abs(displacement.x) >= Math.abs(displacement.z)
                    ? (displacement.x >= 0 ? Direction.EAST : Direction.WEST)
                    : (displacement.z >= 0 ? Direction.SOUTH : Direction.NORTH);
            base = clicked.relative(inward, (int) Math.floor(ShipCockpitRefit.CANDIDATE.width() / 2));
        }
        Vec3 point = Vec3.atBottomCenterOf(base.above());
        var box = ship.getDimensions(ship.getPose()).makeBoundingBox(point);
        var sweep = box.expandTowards(0, -.04, 0);
        if (!ShipSpace.loaded(server, sweep) || !server.mayInteract(placer, clicked)
                || !server.getBlockState(clicked).isFaceSturdy(server, clicked, Direction.UP)
                || !server.mayInteract(placer, base)
                || !server.mayInteract(placer, base.above())
                || !server.getBlockState(base).isFaceSturdy(server, base, Direction.UP)
                || !server.noCollision(ship, box) || !ShipSpace.dryAndSafe(server, sweep)
                || !server.getEntitiesOfClass(Rac1TravelShip.class, box).isEmpty()
                || !server.getEntitiesOfClass(LivingEntity.class, box, living -> !living.isSpectator()).isEmpty()) {
            return fail(placer, "place_pad");
        }
        ship.moveTo(point.x, point.y, point.z, placer.getYRot(), 0);
        return server.addFreshEntity(ship) ? InteractionResult.CONSUME : fail(placer, "place_pad");
    }

    private static InteractionResult fail(net.minecraft.world.entity.player.Player player, String key) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(Component.translatable("message.racmc.travel_ship." + key), true);
        }
        return InteractionResult.FAIL;
    }
}
