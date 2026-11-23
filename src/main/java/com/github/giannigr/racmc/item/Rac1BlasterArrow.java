package com.github.giannigr.racmc.item;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;

/**
 * An arrow that the Blaster fired.
 *
 * <p>The arrow of the Blaster is an arrow of the game of blocks with two differences of its own:
 * it flies straight, without the fall of the arrows of the game, and it is a kind of entity of
 * its own, so that every client knows which arrows the Blaster fired.
 *
 * <p>The kind of the entity is the mark that every client knows: the mark that is written in the
 * data of the entity is not sent to the clients of the game, but the kind of the entity is sent
 * with every arrow that is fired. The arrows of the bow, of the crossbow and of the other mods
 * keep their own kind, they keep their own fall and they are drawn as they always were.
 *
 * <p>The arrow of the Blaster is an ordinary arrow in every other way: it flies with the speed it
 * was shot with, it hits with the damage of the game of blocks, it belongs to the player that
 * shot it, it is never picked up and it is removed after the ticks of the flight of the proof of
 * concept.
 */
public class Rac1BlasterArrow extends Arrow {
    private boolean terminalVisual;
    private java.util.function.BooleanSupplier collisionCurrent = () -> true;

    /** Snapshot before protection listeners can replace a collision target. Late callbacks may
     * deny, never broaden the registered whitelist or reinterpret a new placement as the old hit.
     */
    public void captureCollision(net.minecraft.world.phys.HitResult hit) {
        collisionCurrent = () -> true;
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block) {
            var pos = block.getBlockPos().immutable();
            var state = level().getBlockState(pos);
            var lifecycle = level().getBlockEntity(pos);
            collisionCurrent = () -> level().getBlockState(pos) == state && level().getBlockEntity(pos) == lifecycle;
        } else if (hit instanceof net.minecraft.world.phys.EntityHitResult entity
                && entity.getEntity() instanceof net.minecraft.world.entity.item.FallingBlockEntity falling) {
            var state = falling.getBlockState();
            var data = falling.blockData;
            var position = falling.position();
            java.util.UUID id = data != null && data.hasUUID("BoltRewardId") ? data.getUUID("BoltRewardId") : null;
            collisionCurrent = () -> !falling.isRemoved() && falling.getBlockState() == state
                    && falling.blockData == data && falling.position().equals(position)
                    && (id == null || data.hasUUID("BoltRewardId") && id.equals(data.getUUID("BoltRewardId")));
        }
    }

    /** Read-only render state; never changes arrow collision or flight. */
    public boolean visualFlying() { return !inGround && !isRemoved(); }
    public void visualEnd() {
        if (!terminalVisual && !level().isClientSide()) {
            terminalVisual = true;
            com.github.giannigr.racmc.effect.Rac1EffectTriggers.end(this);
        }
    }
    @Override protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit) {
        var tangent = getDeltaMovement();
        boolean current = collisionCurrent.getAsBoolean();
        collisionCurrent = () -> true;
        boolean removedCrate = current && !level().isClientSide()
                && com.github.giannigr.racmc.item.weapon.RangedCrateHits.grounded(this, hit.getBlockPos());
        if (removedCrate || !current) discard();
        else super.onHitBlock(hit);
        if (!terminalVisual && !level().isClientSide()) {
            terminalVisual = true;
            var face = hit.getDirection();
            com.github.giannigr.racmc.effect.Rac1EffectTriggers.impact(this, hit.getLocation(), tangent,
                    new net.minecraft.world.phys.Vec3(face.getStepX(), face.getStepY(), face.getStepZ()));
        }
    }
    @Override protected void onHitEntity(net.minecraft.world.phys.EntityHitResult hit) {
        var tangent = getDeltaMovement();
        boolean current = collisionCurrent.getAsBoolean();
        collisionCurrent = () -> true;
        if (!current) discard();
        else if (hit.getEntity() instanceof net.minecraft.world.entity.item.FallingBlockEntity carrier
                && com.github.giannigr.racmc.item.weapon.RangedCrateHits.eligible(carrier.getBlockState())) {
            if (!level().isClientSide())
                com.github.giannigr.racmc.item.weapon.RangedCrateHits.airborne(this, carrier);
            discard(); // accepted collision terminates even if the crate mutation was protected
        } else hitWithoutEmbedding(hit.getEntity());
        // A rejected vanilla damage hit can bounce and keep flying: don't invent an end.
        if (isRemoved() && !terminalVisual && !level().isClientSide()) {
            terminalVisual = true;
            com.github.giannigr.racmc.effect.Rac1EffectTriggers.impact(this, hit.getLocation(), tangent,
                    tangent.lengthSqr() > 1.0e-8 ? tangent.normalize().scale(-1)
                            : new net.minecraft.world.phys.Vec3(0, 1, 0));
        }
    }

    @Override protected boolean canHitEntity(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof net.minecraft.world.entity.item.FallingBlockEntity falling
                && !falling.isRemoved() && falling.level() == level()
                && com.github.giannigr.racmc.item.weapon.RangedCrateHits.eligible(falling.getBlockState()))
            return true;
        return super.canHitEntity(entity);
    }

    @Override public void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        // This entity has no verified ordinary original-impact mapping. Inherited block-hit
        // delivery must not play a vanilla arrow shaft impact while entity hits stay silent.
    }

    @Override public void setOwner(@javax.annotation.Nullable net.minecraft.world.entity.Entity owner) {
        super.setOwner(owner);
        pickup = Pickup.DISALLOWED; // vanilla owner/deflection changes can otherwise promote pickup
    }

    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        terminalVisual = tag.getBoolean("RacBlasterTerminalVisual");
        pickup = Pickup.DISALLOWED;
        setNoGravity(true);
    }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("RacBlasterTerminalVisual", terminalVisual);
    }

    /**
     * The supported Blaster payload is an ordinary, nonpiercing arrow. Keep its source-pinned
     * damage/enchantment/fire/Enderman/rejection semantics without asking AbstractArrow to add
     * a physical shaft to the victim. ProjectileImpactEvent/PvP/deflection and the land game
     * event still run in the inherited tick/Projectile dispatcher, before/around this callback.
     * This does not write any victim arrow count, nor modify piercing to suppress graphics.
     */
    private void hitWithoutEmbedding(net.minecraft.world.entity.Entity victim) {
        var owner = getOwner();
        var damage = damageSources().arrow(this, owner == null ? this : owner);
        double base = getBaseDamage();
        if (getWeaponItem() != null && level() instanceof net.minecraft.server.level.ServerLevel server)
            base = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(
                    server, getWeaponItem(), victim, damage, (float) base);
        int amount = net.minecraft.util.Mth.ceil(net.minecraft.util.Mth.clamp(
                getDeltaMovement().length() * base, 0, Integer.MAX_VALUE));
        if (isCritArrow())
            amount = (int) Math.min((long) amount + random.nextInt(amount / 2 + 2), Integer.MAX_VALUE);
        if (owner instanceof LivingEntity living) living.setLastHurtMob(victim);
        boolean enderman = victim.getType() == EntityType.ENDERMAN;
        int oldFire = victim.getRemainingFireTicks();
        if (isOnFire() && !enderman) victim.igniteForSeconds(5);
        if (!victim.hurt(damage, 1.5f * amount)) {
            victim.setRemainingFireTicks(oldFire);
            deflect(net.minecraft.world.entity.projectile.ProjectileDeflection.REVERSE, victim, owner, false);
            setDeltaMovement(getDeltaMovement().scale(.2));
            if (!level().isClientSide() && getDeltaMovement().lengthSqr() < 1e-7) discard();
            return;
        }
        if (enderman) return;
        if (victim instanceof LivingEntity living) {
            doKnockback(living, damage);
            if (level() instanceof net.minecraft.server.level.ServerLevel server)
                net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffectsWithItemSource(
                        server, living, damage, getWeaponItem());
            doPostHurtEffects(living);
            if (living != owner && living instanceof net.minecraft.world.entity.player.Player
                    && owner instanceof net.minecraft.server.level.ServerPlayer server && !isSilent())
                server.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.ARROW_HIT_PLAYER, 0));
        }
        // Ordinary original impact remains unbound; no guessed cue or vanilla arrow hit noise.
        discard();
    }

    /**
     * Creates an arrow of the Blaster that is in the world.
     *
     * @param type the kind of the entity of the arrow
     * @param level the world the arrow is in
     */
    public Rac1BlasterArrow(EntityType<? extends Rac1BlasterArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        pickup = Pickup.DISALLOWED;
    }

    /**
     * Creates an arrow of the Blaster that a player shot.
     *
     * @param type the kind of the entity of the arrow
     * @param level the world the arrow is in
     * @param owner the player that shot the arrow
     */
    public Rac1BlasterArrow(EntityType<? extends Rac1BlasterArrow> type, Level level,
            LivingEntity owner) {
        super(type, level);
        this.setOwner(owner);
        this.setNoGravity(true);
        pickup = Pickup.DISALLOWED;
    }
}
