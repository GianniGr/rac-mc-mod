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

    /**
     * Creates an arrow of the Blaster that is in the world.
     *
     * @param type the kind of the entity of the arrow
     * @param level the world the arrow is in
     */
    public Rac1BlasterArrow(EntityType<? extends Rac1BlasterArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
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
    }
}
