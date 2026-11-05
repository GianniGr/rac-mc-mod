package com.github.giannigr.racmc.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The firing of the Blaster on the server.
 *
 * <p>The server is the only place where the Blaster fires: the client never spawns an arrow of
 * its own, so a shot cannot be seen twice and cannot be predicted wrongly. The player holds the
 * trigger, the item is used, and the shots are fired while the item is used.
 *
 * <p>The rules of the weapon are the rules of {@link Rac1FirePolicy}: the creative mode fires
 * without ammunition, the survival mode and the adventure mode need one ordinary arrow for every
 * shot, and the spectator mode never fires. A weapon without ammunition clicks instead of firing.
 *
 * <p>The arrows of the Blaster are the arrows of the game. They are marked in the data of the
 * entity, so that only they are removed after {@link Rac1ArrowLifetime#LIFETIME_TICKS} ticks. The
 * arrows of the game keep their own life, and the life of the arrows of the game is not touched.
 */
public final class Rac1BlasterServer {

    /**
     * The speed of the arrow of the Blaster, in blocks per tick.
     *
     * <p>The arrow of the game flies at about three blocks per tick when a bow is drawn to its
     * full power. The bolt of the Blaster is quicker than that, but two blocks per tick is the
     * value of the proof of concept.
     */
    public static final float ARROW_SPEED = 2.0f;

    /** The volume of the click of the empty weapon. */
    public static final float DRY_FIRE_VOLUME = 0.7f;

    /** The distance the ray of the eyes of the player is followed, in blocks. */
    public static final double SHOT_RANGE = 100.0;

    /** The key of the marker of the arrows of the Blaster in the data of the entity. */
    private static final String MARKER = "racmcBlaster";

    /** The key of the tick of the birth of the arrow in the data of the entity. */
    private static final String BIRTH = "racmcBlasterBirth";

    /** The key of the tick of the last shot of a player in the data of the player. */
    private static final String LAST_SHOT = "racmcBlasterLastShot";

    /** The key of the tick of the last click of the empty weapon in the data of a player. */
    private static final String LAST_CLICK = "racmcBlasterLastClick";

    private Rac1BlasterServer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Fires the first shot of a burst.
     *
     * <p>The first shot is fired at once, when the trigger is pulled. The weapon keeps being used
     * afterwards, so that the shots of the burst follow.
     *
     * @param level the level the player is in
     * @param player the player that pulled the trigger
     * @return {@code false} when the mouth of the weapon is blocked and no shot was made
     */
    public static boolean fireFirstShot(Level level, Player player) {
        Rac1BurstState state = stateOf(player);
        state.beginPull();
        long now = level.getGameTime();
        boolean fired;
        switch (Rac1FirePolicy.evaluate(modeOf(player), countArrows(player))) {
            case FIRE_WITHOUT_AMMO -> fired = fire(level, player, state, now, false);
            case FIRE_WITH_AMMO -> fired = fire(level, player, state, now, true);
            case DRY_FIRE -> {
                dryFire(level, player, state, now);
                fired = false;
            }
            case BLOCKED -> {
                // The spectator mode never fires and never clicks.
                fired = false;
            }
            default -> fired = false;
        }
        saveState(player, state);
        return fired;
    }

    /**
     * Fires the shots of a burst while the trigger is held.
     *
     * <p>The method is called once per tick of the server. A shot is only fired when the time of
     * the shot is due, so that the weapon fires ten shots a second and not twenty.
     *
     * @param level the level the player is in
     * @param player the player that holds the trigger
     */
    public static void fireWhileHeld(Level level, Player player) {
        Rac1BurstState state = stateOf(player);
        long now = level.getGameTime();
        if (!state.shotDue(now)) {
            return;
        }
        switch (Rac1FirePolicy.evaluate(modeOf(player), countArrows(player))) {
            case FIRE_WITHOUT_AMMO -> fire(level, player, state, now, false);
            case FIRE_WITH_AMMO -> fire(level, player, state, now, true);
            case DRY_FIRE -> dryFire(level, player, state, now);
            case BLOCKED -> {
                // The spectator mode never fires and never clicks.
            }
        }
        saveState(player, state);
    }

    /**
     * Forgets the burst of a player.
     *
     * <p>The burst is forgotten when the trigger is released, so that the next pull of the
     * trigger fires at once.
     *
     * @param player the player that released the trigger
     */
    public static void resetBurst(Player player) {
        Rac1BurstState state = stateOf(player);
        state.reset();
        saveState(player, state);
    }

    /**
     * The way of play of a player.
     *
     * @param player the player
     * @return the way of play of the player
     */
    public static Rac1GameMode modeOf(Player player) {
        if (player instanceof ServerPlayer server) {
            return switch (server.gameMode.getGameModeForPlayer()) {
                case CREATIVE -> Rac1GameMode.CREATIVE;
                case SURVIVAL -> Rac1GameMode.SURVIVAL;
                case ADVENTURE -> Rac1GameMode.ADVENTURE;
                case SPECTATOR -> Rac1GameMode.SPECTATOR;
            };
        }
        return Rac1GameMode.SURVIVAL;
    }

    /**
     * The number of ordinary arrows a player carries.
     *
     * <p>Only the ordinary arrows of the game count: the tipped arrows and the spectral arrows
     * of the game are not ammunition of the Blaster.
     *
     * @param player the player
     * @return the number of ordinary arrows
     */
    public static int countArrows(Player player) {
        int count = 0;
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.ARROW)) {
            count += offhand.getCount();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.ARROW)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Fires a shot of the Blaster.
     *
     * @param level the level the player is in
     * @param player the player that fires
     * @param state the bookkeeping of the burst
     * @param now the tick of the shot
     * @param withAmmo whether an ordinary arrow is consumed for the shot
     * @return {@code true} when the shot was made
     */
    private static boolean fire(Level level, Player player, Rac1BurstState state, long now,
                                boolean withAmmo) {
        Arrow arrow = spawnArrow(level, player);
        if (arrow == null) {
            // The mouth of the weapon is blocked: no shot is made, no ammunition is used and the
            // weapon stops being used, so that the shot is not drawn either.
            player.stopUsingItem();
            return false;
        }
        if (withAmmo && !consumeArrow(player)) {
            // The shot is only paid for when the ammunition is really there. The arrow is taken
            // out of the world again, so that no shot is given away.
            arrow.discard();
            return false;
        }
        state.recordShot(now);
        return true;
    }

    /**
     * Plays the click of the empty weapon.
     *
     * <p>The click is played on the server, so that it is heard by the player that fired and by
     * the players around, and it is played once only: the bookkeeping of the burst holds the
     * click of a pull of the trigger and the bound on its rate.
     *
     * @param level the level the player is in
     * @param player the player that holds the empty weapon
     * @param state the bookkeeping of the burst
     * @param now the tick of the click
     */
    private static void dryFire(Level level, Player player, Rac1BurstState state, long now) {
        if (!state.dryClickAllowed(now)) {
            return;
        }
        state.recordDryClick(now);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK,
                SoundSource.PLAYERS, DRY_FIRE_VOLUME, 1.0f);
    }

    /**
     * Spawns the arrow of a shot of the Blaster.
     *
     * <p>The arrow is an arrow of the game: it flies with the gravity and the drag of the game,
     * it hits with the damage of the game, and it is never picked up.
     *
     * <p>The arrow leaves the mouth of the weapon and not the head of the shooter: the place of
     * the mouth is built out of the frame of the weapon and out of the place of the player, and it
     * is the same place the weapon is drawn at in the third person. When the mouth of the weapon
     * stands inside a wall, or when the way from the eyes of the player to the mouth is blocked,
     * no shot is made at all: the shot is not made through a wall, no ammunition is used and the
     * weapon stops being used, so that the shot is not drawn either.
     *
     * @param level the level the player is in
     * @param player the player that fires
     * @return the arrow, or {@code null} when the shot is blocked
     */
    private static Arrow spawnArrow(Level level, Player player) {
        InteractionHand hand = handOf(player);
        // The mouth of the weapon, in the world. The place is built out of the frame of the
        // weapon and out of the place of the player, and it is the same place the weapon is
        // drawn at in the third person.
        Rac1BlasterGrip.Look look = lookOf(player);
        Rac1BlasterGrip.Point muzzle = Rac1BlasterGrip.muzzle(player.getX(), player.getY(),
                player.getZ(), look);
        Vec3 spawn = new Vec3(muzzle.x(), muzzle.y(), muzzle.z());
        Vec3 eyes = player.getEyePosition();
        if (Rac1BlasterObstruction.blocked(worldOf(level, player), new double[] { eyes.x, eyes.y,
                eyes.z }, new double[] { spawn.x, spawn.y, spawn.z })) {
            return null;
        }
        Vec3 target = target(player, eyes);
        Rac1BlasterGrip.Direction direction = Rac1BlasterGrip.direction(spawn.x, spawn.y, spawn.z,
                target.x, target.y, target.z);
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW),
                player.getItemInHand(hand));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setPos(spawn.x, spawn.y, spawn.z);
        if (direction.x() != 0.0 || direction.y() != 0.0 || direction.z() != 0.0) {
            arrow.shoot(direction.x(), direction.y(), direction.z(), ARROW_SPEED, 0.0f);
        } else {
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, ARROW_SPEED,
                    0.0f);
        }
        CompoundTag data = arrow.getPersistentData();
        data.putBoolean(MARKER, true);
        data.putLong(BIRTH, level.getGameTime());
        if (!level.addFreshEntity(arrow)) {
            return null;
        }
        return arrow;
    }

    /**
     * The look of a player.
     *
     * <p>The weapon is held by the arm of the player, and the arm follows the body of the player.
     * The turn of the head of the player around the body is therefore the turn that matters for
     * the weapon, and the turn of the body of the player is the turn of the weapon in the world.
     *
     * @param player the player
     * @return the look of the player
     */
    private static Rac1BlasterGrip.Look lookOf(Player player) {
        return new Rac1BlasterGrip.Look(
                Mth.wrapDegrees(player.getYRot() - player.yBodyRot), player.getXRot(),
                player.yBodyRot);
    }

    /**
     * The world a shot of the Blaster is made in.
     *
     * @param level the level the player is in
     * @param player the player that fires
     * @return the world
     */
    private static Rac1BlasterObstruction.World worldOf(Level level, Player player) {
        return new Rac1BlasterObstruction.World() {

            @Override
            public boolean solidAt(double x, double y, double z) {
                return isSolid(level, new Vec3(x, y, z));
            }

            @Override
            public boolean pathBlocked(double fromX, double fromY, double fromZ, double toX,
                                       double toY, double toZ) {
                ClipContext context = new ClipContext(new Vec3(fromX, fromY, fromZ),
                        new Vec3(toX, toY, toZ), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        player);
                return level.clip(context).getType() != HitResult.Type.MISS;
            }
        };
    }

    /**
     * The place the shot of the Blaster goes to.
     *
     * <p>The shot goes to the place the player looks at: the ray of the eyes of the player is
     * followed, and the shot goes from the mouth of the weapon to the place where the ray met
     * something.
     *
     * @param player the player that fires
     * @param eyes the place of the eyes of the player
     * @return the place the shot goes to
     */
    private static Vec3 target(Player player, Vec3 eyes) {
        HitResult trace = player.pick(SHOT_RANGE, 1.0f, false);
        if (trace.getType() == HitResult.Type.MISS) {
            return eyes.add(player.getLookAngle().scale(SHOT_RANGE));
        }
        return trace.getLocation();
    }

    /**
     * Whether a place is inside a block that stops a shot.
     *
     * @param level the level the place is in
     * @param place the place
     * @return {@code true} when the place is inside a block
     */
    private static boolean isSolid(Level level, Vec3 place) {
        BlockPos block = BlockPos.containing(place);
        BlockState state = level.getBlockState(block);
        return !state.isAir() && state.blocksMotion();
    }

    /**
     * Consumes one ordinary arrow of a player.
     *
     * <p>The arrow of the other hand is used first, and then the arrows of the inventory of the
     * player. Only the ordinary arrows of the game are used.
     *
     * @param player the player that fires
     * @return {@code true} when an arrow was consumed
     */
    private static boolean consumeArrow(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.ARROW) && offhand.getCount() > 0) {
            offhand.shrink(1);
            return true;
        }
        for (int index = 0; index < player.getInventory().items.size(); index++) {
            ItemStack stack = player.getInventory().items.get(index);
            if (stack.is(Items.ARROW) && stack.getCount() > 0) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes an arrow of the Blaster whose flight is over.
     *
     * <p>Only the arrows that the Blaster fired are removed. The arrows of the game keep their
     * own life, and the life of the arrows of the game is not touched.
     *
     * @param entity the entity that was ticked
     */
    public static void removeExpiredArrow(Entity entity) {
        if (entity.level().isClientSide() || !(entity instanceof AbstractArrow arrow)) {
            return;
        }
        CompoundTag data = arrow.getPersistentData();
        if (!data.getBoolean(MARKER)) {
            return;
        }
        if (Rac1ArrowLifetime.expired(data.getLong(BIRTH), entity.level().getGameTime())) {
            arrow.discard();
        }
    }

    /**
     * The bookkeeping of the burst of a player.
     *
     * <p>The bookkeeping is kept in the data of the player, so that it survives a change of the
     * dimension and the moment the player leaves and comes back.
     *
     * @param player the player
     * @return the bookkeeping of the burst
     */
    private static Rac1BurstState stateOf(Player player) {
        CompoundTag data = player.getPersistentData();
        Rac1BurstState state = new Rac1BurstState();
        state.restore(data.getLong(LAST_SHOT), data.getLong(LAST_CLICK));
        return state;
    }

    /**
     * Writes the bookkeeping of the burst of a player back into its data.
     *
     * @param player the player
     * @param state the bookkeeping of the burst
     */
    private static void saveState(Player player, Rac1BurstState state) {
        CompoundTag data = player.getPersistentData();
        data.putLong(LAST_SHOT, state.lastShotTick());
        data.putLong(LAST_CLICK, state.lastDryClickTick());
    }

    /**
     * The hand the weapon is held in.
     *
     * <p>The weapon is used in the hand the player holds it in, and the shot leaves the weapon of
     * that hand.
     *
     * @param player the player that fires
     * @return the hand of the weapon
     */
    private static InteractionHand handOf(Player player) {
        return player.isUsingItem() ? player.getUsedItemHand() : InteractionHand.MAIN_HAND;
    }
}
