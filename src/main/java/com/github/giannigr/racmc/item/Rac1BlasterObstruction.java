package com.github.giannigr.racmc.item;

/**
 * The rule that decides whether a shot of the Blaster is blocked.
 *
 * <p>A shot of the Blaster leaves the mouth of the weapon, and the mouth of the weapon stands
 * more than half a block in front of the player. A player who stands close to a wall therefore
 * puts the mouth of the weapon into the wall, and a shot must not be made through it.
 *
 * <p>The rule asks the world two questions: whether the mouth of the weapon stands inside a block
 * that stops a shot, and whether the way from the eyes of the player to the mouth of the weapon
 * is free. The world is an interface, so that the rule can be checked without a world.
 */
public final class Rac1BlasterObstruction {

    private Rac1BlasterObstruction() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The questions the rule asks the world.
     */
    public interface World {

        /**
         * Whether a place stands inside a block that stops a shot.
         *
         * @param x the place along the first axis
         * @param y the place along the second axis
         * @param z the place along the third axis
         * @return {@code true} when the place stands inside a block
         */
        boolean solidAt(double x, double y, double z);

        /**
         * Whether the straight way from one place to another is blocked.
         *
         * @param fromX the first place along the first axis
         * @param fromY the first place along the second axis
         * @param fromZ the first place along the third axis
         * @param toX the second place along the first axis
         * @param toY the second place along the second axis
         * @param toZ the second place along the third axis
         * @return {@code true} when the way is blocked
         */
        boolean pathBlocked(double fromX, double fromY, double fromZ, double toX, double toY,
                            double toZ);
    }

    /**
     * Whether a shot that leaves the mouth of the weapon is blocked.
     *
     * @param world the world the shot is made in
     * @param eyes the place of the eyes of the player
     * @param mouth the place of the mouth of the weapon
     * @return {@code true} when the shot must not be made
     */
    public static boolean blocked(World world, double[] eyes, double[] mouth) {
        if (world.solidAt(mouth[0], mouth[1], mouth[2])) {
            // The mouth of the weapon stands inside a wall.
            return true;
        }
        // The way from the eyes of the player to the mouth of the weapon must be free, so that a
        // shot is not made out of a wall.
        return world.pathBlocked(eyes[0], eyes[1], eyes[2], mouth[0], mouth[1], mouth[2]);
    }
}
