package com.github.giannigr.racmc.item;

/**
 * The place the weapon of the game is held at, and the place its shots leave it from.
 *
 * <p>The contract is shared by the server, which creates the arrows, and by the renderer, which
 * draws the weapon. It holds no Minecraft types, so it can be checked on a plain virtual machine.
 *
 * <p>The chain of the game was read out of the source of the game of blocks, of the version
 * 1.21.1 with NeoForge 21.1.265:
 *
 * <ul>
 *   <li>the item of the game is drawn in the cube of an item of the game; the transforms of the
 *       model of the item move that cube - the place in sixteenths of a block, the turn around
 *       the third axis, then around the first, then around the second, and the size last;</li>
 *   <li>the game then moves the cube by half a block in every direction, because the cube stands
 *       in the middle of the place of the item; the renderer of the Blaster takes that move back,
 *       exactly, because the weapon of the game does not use that cube;</li>
 *   <li>in the third person the item is held by the joint of the arm of the player: the game
 *       moves the joint of the arm, turns it by a quarter around the first axis, turns it by half
 *       a turn around the second axis and moves it by a sixteenth of a block to the side, by an
 *       eighth of a block up and by five eighths of a block back.</li>
 * </ul>
 *
 * <p>Out of that chain the frame of the item follows: in the third person the first axis of the
 * item is the side of the player, the second axis is the direction he looks at and the third axis
 * points down. The joint of the arm is turned with the look of the player, so the frame turns
 * with it.
 *
 * <p>The mouth of the weapon was measured out of the mesh of the Blaster of the game: the barrel
 * is the narrow part at the front of the weapon, its bore runs along the third axis of the mesh,
 * and the mouth is the ring at the end of it.
 */
public final class Rac1BlasterGrip {

    /**
     * The turn of the model of the item in the third person, in degrees.
     *
     * <p>With this turn the mouth of the weapon - the third axis of the mesh - points into the
     * direction the player looks at, and the point of the game that holds the weapon stays in the
     * hand of the player.
     */
    public static final double[] THIRD_PERSON_ROTATION = { -90.0, 0.0, 0.0 };

    /**
     * The turn of the model of the item in the first person, in degrees.
     *
     * <p>With this turn the mouth of the weapon points into the screen, away from the player.
     */
    public static final double[] FIRST_PERSON_ROTATION = { 0.0, 180.0, 0.0 };

    /** The turn of the model of the item in the lists of the game, in degrees. */
    public static final double[] GUI_ROTATION = { 0.0, 90.0, 0.0 };

    /**
     * The turn of the arm of the player while he holds an item, in degrees.
     *
     * <p>This is the pose of the arm of the game for an item that is held: the arm hangs at the
     * side of the player and is turned a little forward. The game uses the same pose for every
     * item, and the frame of the item follows it.
     */
    public static final double ARM_POSE = -18.0;

    /** The place of the mouth of the weapon in the frame of the mesh, along the first axis. */
    public static final double MUZZLE_X = 0.24;

    /** The place of the mouth of the weapon in the frame of the mesh, along the second axis. */
    public static final double MUZZLE_Y = -0.03;

    /** The place of the mouth of the weapon in the frame of the mesh, along the third axis. */
    public static final double MUZZLE_Z = 0.633;

    /** The place of the joint of the arm of the player in the frame of the model of the player. */
    private static final double[] ARM_JOINT = { -5.0 / 16.0, 2.0 / 16.0, 0.0 };

    /** The place of the item in the joint of the arm of the player. */
    private static final double[] ITEM_IN_JOINT = { 1.0 / 16.0, 0.125, -0.625 };

    /** The place of the hand of the player in the frame of the camera of the first person. */
    private static final double[] FIRST_PERSON_HAND = { 0.56, -0.52, -0.72 };

    private Rac1BlasterGrip() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A place in the world.
     *
     * @param x the place along the first axis
     * @param y the place along the second axis
     * @param z the place along the third axis
     */
    public record Point(double x, double y, double z) {
    }

    /**
     * The direction from one place to another.
     *
     * @param fromX the first place along the first axis
     * @param fromY the first place along the second axis
     * @param fromZ the first place along the third axis
     * @param toX the second place along the first axis
     * @param toY the second place along the second axis
     * @param toZ the second place along the third axis
     * @return the direction from the first place to the second one
     */
    public static Point direction(double fromX, double fromY, double fromZ, double toX, double toY,
                                  double toZ) {
        double length = Math.sqrt((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY)
                + (toZ - fromZ) * (toZ - fromZ));
        if (length <= 0.0) {
            return new Point(0.0, 0.0, 0.0);
        }
        return new Point((toX - fromX) / length, (toY - fromY) / length, (toZ - fromZ) / length);
    }

    /**
     * The three axes of the item in the frame of the model of the player.
     *
     * <p>The first axis is the side of the player, the second axis is the direction he looks at
     * and the third axis points down. The joint of the arm is turned with the look of the player,
     * so the frame turns with it.
     *
     * @param pitch the turn of the arm of the player, in degrees
     * @param axes three arrays of three numbers, the axes of the item
     */
    public static void axes(double pitch, double[][] axes) {
        // The axes of the item are the axes of the model of the player, until the game turns
        // them: the game turns the joint of the arm by half a turn around the second axis and by
        // a quarter around the first axis, and the joint of the arm is turned with the look of the
        // player.
        double[] ex = { 1.0, 0.0, 0.0 };
        double[] ey = { 0.0, 1.0, 0.0 };
        double[] ez = { 0.0, 0.0, 1.0 };
        for (double[] axis : new double[][] { ex, ey, ez }) {
            rotateY(axis, 180.0);
            rotateX(axis, -90.0);
            rotateX(axis, pitch);
        }
        axes[0] = ex;
        axes[1] = ey;
        axes[2] = ez;
    }

    /**
     * The place of the item in the joint of the arm of the player, in the model of the player.
     *
     * <p>The place is the joint of the arm of the player, moved by the place of the item in the
     * joint, with the joint turned by the look of the player.
     *
     * @param pitch the turn of the arm of the player, in degrees
     * @return the place in the model of the player
     */
    public static Point originInModel(double pitch) {
        double[] item = { ITEM_IN_JOINT[0], ITEM_IN_JOINT[1], ITEM_IN_JOINT[2] };
        rotateY(item, 180.0);
        rotateX(item, -90.0);
        rotateX(item, pitch);
        return new Point(ARM_JOINT[0] + item[0], ARM_JOINT[1] + item[1], ARM_JOINT[2] + item[2]);
    }

    /**
     * The place of the hand of the player in the world, in the third person.
     *
     * @param x the place of the player along the first axis
     * @param y the place of the player along the second axis
     * @param z the place of the player along the third axis
     * @param yaw the turn of the player around the vertical axis, in degrees
     * @param pitch the turn of the arm of the player, in degrees
     * @return the place of the hand of the player
     */
    public static Point hand(double x, double y, double z, double yaw, double pitch) {
        Point origin = originInModel(pitch);
        double[] model = { origin.x(), origin.y(), origin.z() };
        // The model of the player is turned with him.
        double[] world = toWorld(model, yaw);
        return new Point(x + world[0], y + world[1], z + world[2]);
    }

    /**
     * The place of the mouth of the weapon in the world, in the third person.
     *
     * @param x the place of the player along the first axis
     * @param y the place of the player along the second axis
     * @param z the place of the player along the third axis
     * @param yaw the turn of the player around the vertical axis, in degrees
     * @param pitch the turn of the arm of the player, in degrees
     * @return the place of the mouth of the weapon
     */
    public static Point muzzle(double x, double y, double z, double yaw, double pitch) {
        Point hand = hand(x, y, z, yaw, pitch);
        double[][] axes = new double[3][];
        axes(pitch, axes);
        // The mouth of the weapon is turned with the model of the item.
        double[] mouth = { MUZZLE_X, MUZZLE_Y, MUZZLE_Z };
        turn(mouth, THIRD_PERSON_ROTATION);
        double[] world = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            world[axis] = axes[axis][0] * mouth[0] + axes[axis][1] * mouth[1]
                    + axes[axis][2] * mouth[2];
        }
        double[] turned = toWorld(world, yaw);
        return new Point(hand.x() + turned[0], hand.y() + turned[1], hand.z() + turned[2]);
    }

    /**
     * The direction the weapon points at, in the world, in the third person.
     *
     * @param pitch the turn of the arm of the player, in degrees
     * @param yaw the turn of the player around the vertical axis, in degrees
     * @return the direction of the weapon
     */
    public static Point barrel(double pitch, double yaw) {
        double[][] axes = new double[3][];
        axes(pitch, axes);
        // The mouth of the weapon is at the front of the weapon, along its third axis.
        double[] mouth = { 0.0, 0.0, 1.0 };
        turn(mouth, THIRD_PERSON_ROTATION);
        double[] world = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            world[axis] = axes[axis][0] * mouth[0] + axes[axis][1] * mouth[1]
                    + axes[axis][2] * mouth[2];
        }
        double[] turned = toWorld(world, yaw);
        double length = Math.sqrt(turned[0] * turned[0] + turned[1] * turned[1]
                + turned[2] * turned[2]);
        if (length <= 0.0) {
            return new Point(0.0, 0.0, 0.0);
        }
        return new Point(turned[0] / length, turned[1] / length, turned[2] / length);
    }

    /**
     * The place of the mouth of the weapon in the world, in the first person.
     *
     * <p>The first person draws the weapon in the frame of the camera of the player, so the place
     * of the mouth is the place of the hand of the camera plus the mouth of the weapon, turned
     * with the model of the item of the first person.
     *
     * @param eyeX the place of the eyes of the player along the first axis
     * @param eyeY the place of the eyes of the player along the second axis
     * @param eyeZ the place of the eyes of the player along the third axis
     * @param yaw the turn of the player around the vertical axis, in degrees
     * @param pitch the turn of the player around the side axis, in degrees
     * @return the place of the mouth of the weapon
     */
    public static Point firstPersonMuzzle(double eyeX, double eyeY, double eyeZ, double yaw,
                                           double pitch) {
        double[][] camera = cameraAxes(yaw, pitch);
        double[] hand = { FIRST_PERSON_HAND[0], FIRST_PERSON_HAND[1], FIRST_PERSON_HAND[2] };
        double[] mouth = { MUZZLE_X, MUZZLE_Y, MUZZLE_Z };
        turn(mouth, FIRST_PERSON_ROTATION);
        double[] world = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            world[axis] = camera[axis][0] * hand[0] + camera[axis][1] * hand[1]
                    + camera[axis][2] * hand[2] + camera[axis][0] * mouth[0]
                    + camera[axis][1] * mouth[1] + camera[axis][2] * mouth[2];
        }
        return new Point(eyeX + world[0], eyeY + world[1], eyeZ + world[2]);
    }

    /**
     * The axes of the camera of the player in the world.
     *
     * @param yaw the turn of the player around the vertical axis, in degrees
     * @param pitch the turn of the player around the side axis, in degrees
     * @return the axes of the camera
     */
    private static double[][] cameraAxes(double yaw, double pitch) {
        double[] right = { -Math.cos(Math.toRadians(yaw)), 0.0, -Math.sin(Math.toRadians(yaw)) };
        double[] up = { 0.0, 1.0, 0.0 };
        double[] backward = { Math.sin(Math.toRadians(yaw)), 0.0, -Math.cos(Math.toRadians(yaw)) };
        double[] forward = { -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) };
        double[] side = { right[0], right[1], right[2] };
        rotateX(side, pitch);
        double[] top = { up[0], up[1], up[2] };
        rotateX(top, pitch);
        return new double[][] { side, top, backward };
    }

    /**
     * Turns a place of the model of the player into the world.
     *
     * @param model the place in the model of the player
     * @param yaw the turn of the player around the vertical axis, in degrees
     * @return the place in the world
     */
    private static double[] toWorld(double[] model, double yaw) {
        double[] right = { -Math.cos(Math.toRadians(yaw)), 0.0, -Math.sin(Math.toRadians(yaw)) };
        double[] up = { 0.0, 1.0, 0.0 };
        double[] backward = { Math.sin(Math.toRadians(yaw)), 0.0, -Math.cos(Math.toRadians(yaw)) };
        return new double[] {
                model[0] * right[0] + model[1] * up[0] + model[2] * backward[0],
                model[0] * right[1] + model[1] * up[1] + model[2] * backward[1],
                model[0] * right[2] + model[1] * up[2] + model[2] * backward[2] };
    }

    /**
     * Turns a place with the turn of the model of the item.
     *
     * <p>The turn of the game is applied around the third axis first, then around the first, then
     * around the second.
     *
     * @param place the place that is turned
     * @param rotation the turn in degrees
     */
    private static void turn(double[] place, double[] rotation) {
        rotateZ(place, rotation[2]);
        rotateX(place, rotation[0]);
        rotateY(place, rotation[1]);
    }

    private static void rotateX(double[] place, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double y = place[1];
        double z = place[2];
        place[1] = y * cos - z * sin;
        place[2] = y * sin + z * cos;
    }

    private static void rotateY(double[] place, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = place[0];
        double z = place[2];
        place[0] = x * cos + z * sin;
        place[2] = -x * sin + z * cos;
    }

    private static void rotateZ(double[] place, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = place[0];
        double y = place[1];
        place[0] = x * cos - y * sin;
        place[1] = x * sin + y * cos;
    }
}
