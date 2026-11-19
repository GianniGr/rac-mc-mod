package com.github.giannigr.racmc.item;

import com.github.giannigr.racmc.item.weapon.HandPoseSnapshot;
import com.github.giannigr.racmc.item.weapon.HandTransform;
import com.github.giannigr.racmc.item.weapon.Rac1BlasterDefinition;
import com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector;

/**
 * The frame of the weapon of the game and the place of its mouth in the world.
 *
 * <p>The contract is shared by the server, which creates the arrows, and by the renderer, which
 * draws the weapon. It holds no Minecraft types, so it can be checked on a plain virtual machine.
 *
 * <h2>The frame of the weapon</h2>
 *
 * <p>The frame of the weapon is the frame of the mesh of the Blaster of the game, as it was
 * imported: the first axis runs up, the second axis is the side of the weapon and the third axis
 * runs along the bore. The numbers were measured out of the class of the weapon of the disc:
 *
 * <ul>
 *   <li>the skeleton of the weapon holds a chain of four joints (the joints 13, 14, 15 and 16 of
 *       the class 168) that runs along the bore. Every one of the four joints stands at the same
 *       place along the first and second axes, {@link #BORE_X} and {@link #BORE_Y}.
 *       Joint16 is near z=.6141, behind the front ring;</li>
 *   <li>the mesh has a ring of twelve vertices near {@link #MUZZLE_Z}, centered around
 *       {@link #BORE_X} and {@link #BORE_Y}. The ring is the
 *       mouth of the barrel.</li>
 * </ul>
 *
 * <p>The origin of the mesh is the socket of the weapon: it is the root joint of the skeleton of
 * the weapon, and the game hangs the weapon on the joint of the hand of the hero with it. The
 * socket is therefore the place the hand of the player holds the weapon at.
 *
 * <h2>The chain of the game of blocks</h2>
 *
 * <p>The place of the mouth of the weapon in the world was read out of the source of the game of
 * blocks, of the version 1.21.1 with NeoForge 21.1.265:
 *
 * <ul>
 *   <li>the item is drawn by the layer of the item in the hand, which moves the joint of the arm
 *       of the player, turns it by a quarter around the first axis, turns it by half a turn
 *       around the second axis and moves the item by a sixteenth of a block to the side, by an
 *       eighth of a block up and by five eighths of a block back;</li>
 *   <li>the joint of the arm of the player is turned around the first axis of the model of the
 *       player and then around the second axis, the way the game of blocks turns it, so the turn
 *       of the arm around the first axis is the turn of the head around the side axis and the
 *       turn of the arm around the second axis is the turn of the head around the vertical
 *       axis;</li>
 *   <li>the model of the item turns the mesh of the weapon around the first axis, then around the
 *       second axis and then around the third axis, the way the game of blocks turns the model of
 *       an item, so the third axis of the turn is turned first;</li>
 *   <li>the game of blocks moves the cube of the item by half a block in every direction before
 *       it hands the item to the renderer; the renderer of the Blaster takes that move back,
 *       exactly;</li>
 *   <li>the renderer of the living entities turns the model of the player around the vertical
 *       axis by the turn of the body of the player, turns it around the vertical axis by a half
 *       turn and turns it upside down, and moves it down by 1.501 blocks.</li>
 * </ul>
 *
 * <p>Out of that chain the world follows. The first axis of the model of the player is the side
 * of the player, the second axis points down and the third axis is the direction the player looks
 * at. The turn of the body of the player is the turn of the model around the vertical axis, and
 * the head of the player may be turned away from the body, so the frame of the weapon follows the
 * body and not the head.
 */
public final class Rac1BlasterGrip {

    /** The place of the axis of the bore of the weapon, along the first axis of the mesh. */
    public static final double BORE_X = Rac1BlasterDefinition.INSTANCE.muzzle().x();

    /** The place of the axis of the bore of the weapon, along the second axis of the mesh. */
    public static final double BORE_Y = Rac1BlasterDefinition.INSTANCE.muzzle().y();

    /** The place of the mouth of the barrel, along the third axis of the mesh. */
    public static final double MUZZLE_Z = Rac1BlasterDefinition.INSTANCE.muzzle().z();

    /** The smallest place of the mesh of the weapon along the first axis, which runs up. */
    public static final double MESH_MIN_X = -0.1830;

    /** The greatest place of the mesh of the weapon along the first axis. */
    public static final double MESH_MAX_X = 0.5272;

    /** The smallest place of the mesh of the weapon along the second axis, the side. */
    public static final double MESH_MIN_Y = -0.1661;

    /** The greatest place of the mesh of the weapon along the second axis. */
    public static final double MESH_MAX_Y = 0.1438;

    /** The smallest place of the mesh of the weapon along the third axis, the bore. */
    public static final double MESH_MIN_Z = -0.1031;

    /** The greatest place of the mesh of the weapon along the third axis. */
    public static final double MESH_MAX_Z = 0.6335;

    /** The place of the joint of the arm of the player, along the first axis of the model. */
    public static final double ARM_JOINT_X = -5.0 / 16.0;

    /** The place of the joint of the arm of the player, along the second axis of the model. */
    public static final double ARM_JOINT_Y = 2.0 / 16.0;

    /** The place of the joint of the arm of the player, along the third axis of the model. */
    public static final double ARM_JOINT_Z = 0.0;

    /** The place of the item in the joint of the arm of the player, along the first axis. */
    public static final double ITEM_IN_JOINT_X = 1.0 / 16.0;

    /** The place of the item in the joint of the arm of the player, along the second axis. */
    public static final double ITEM_IN_JOINT_Y = 0.125;

    /** The place of the item in the joint of the arm of the player, along the third axis. */
    public static final double ITEM_IN_JOINT_Z = -0.625;

    /** The move of the game of blocks below the shoulders of the player, in blocks. */
    public static final double MODEL_HEIGHT = 1.501;

    /**
     * The turn of the model of the item in the third person, in degrees.
     *
     * <p>The turn is around the first axis, then around the second axis and then around the third
     * axis, the way the game of blocks turns the model of an item. With this turn the bore of the
     * weapon points into the direction the player looks at and the weapon stands upright: the up
     * axis of the weapon points into the direction of the sky while the player looks ahead.
     *
     * <p>The turn is the turn of the weapon in the hand of a player whose arm is raised to aim.
     * The arm of the player is raised by the pose of the aim, and the turn of the model of the
     * item keeps the weapon pointing into the direction the player looks at while the arm is
     * raised. The turn of the model of the item is the same for the player that only holds the
     * weapon: with the arm of the player hanging at the side of his body, the weapon points down
     * along the arm.
     */
    public static final double[] THIRD_PERSON_ROTATION = {
            Rac1BlasterDefinition.INSTANCE.itemRotation().x(),
            Rac1BlasterDefinition.INSTANCE.itemRotation().y(),
            Rac1BlasterDefinition.INSTANCE.itemRotation().z() };

    /**
     * The turn of the model of the item in the first person, in degrees.
     *
     * <p>The turn is around the first axis, then around the second axis and then around the third
     * axis, the way the game of blocks turns the model of an item. With this turn the bore of the
     * weapon points away from the eye of the player, into the screen, and the up axis of the
     * weapon points up, into the screen.
     */
    public static final double[] FIRST_PERSON_ROTATION = {
            Rac1BlasterDefinition.INSTANCE.firstPerson().rotation().x(),
            Rac1BlasterDefinition.INSTANCE.firstPerson().rotation().y(),
            Rac1BlasterDefinition.INSTANCE.firstPerson().rotation().z() };

    /**
     * The size of the weapon in the first person, as a share of its size in the game.
     *
     * <p>The size is a provisional reference and not a canonical one: it was looked at in the game
     * and it is open to the review of the owner of the project. The size of the weapon in the
     * world is the size of the game.
     */
    public static final double FIRST_PERSON_SCALE = Rac1BlasterDefinition.INSTANCE.firstPerson().scale();

    /**
     * The place of the hand of the player in the first person, in blocks.
     *
     * <p>The place is the place the game of blocks puts the hand of the player in: five hundred
     * and sixty thousandths of a block to the side, five hundred and twenty thousandths of a
     * block down and seven hundred and twenty thousandths of a block in front of the eye of the
     * player.
     */
    public static final double[] FIRST_PERSON_HAND = { 0.56, -0.52, -0.72 };

    /**
     * The turn of the model of the item in the lists of the game, in degrees.
     *
     * <p>The turn is around the first axis, then around the second axis and then around the third
     * axis, the way the game of blocks turns the model of an item. With this turn the up axis of
     * the weapon runs up in the list and the bore of the weapon runs to the right and a little
     * towards the player, so the picture of the weapon in the lists is the weapon seen from the
     * side and not the weapon seen from above.
     */
    public static final double[] GUI_ROTATION = {
            Rac1BlasterDefinition.INSTANCE.gui().rotation().x(),
            Rac1BlasterDefinition.INSTANCE.gui().rotation().y(),
            Rac1BlasterDefinition.INSTANCE.gui().rotation().z() };

    /**
     * The greatest place of the mouth of the weapon from the eye of the player, in blocks.
     *
     * <p>The mouth of the weapon may not be drawn behind the eye of the player and not far away
     * from it either, so that the picture of the weapon stays a picture of a weapon in the hand
     * of the player.
     */
    public static final double FIRST_PERSON_MOUTH_RANGE = 2.0;

    /**
     * The place of the mouth of the drawn weapon in the space of the camera, in blocks.
     *
     * <p>The mouth of the drawn weapon is drawn at the place of the screen where the mouth of the
     * weapon in the world is drawn, and the depth of the mouth of the drawn weapon is the depth
     * the game of blocks gives it, so that the size of the weapon on the screen and the
     * perspective of the weapon do not change.
     */
    public static final double FIRST_PERSON_MOUTH_DEPTH = 1.068;

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
     * The turns of the arm of the player.
     *
     * @param x the turn of the arm around the first axis of the model of the player, in degrees
     * @param y the turn of the arm around the second axis of the model of the player, in degrees
     */
    public record ArmTurn(double x, double y) {
    }

    /**
     * The look of a player.
     *
     * @param headYaw the turn of the head of the player around the body of the player, in degrees
     * @param headPitch the turn of the head of the player around the side axis, in degrees
     * @param bodyYaw the turn of the body of the player around the vertical axis, in degrees
     */
    public record Look(double headYaw, double headPitch, double bodyYaw) {
    }

    /**
     * A direction in the world.
     *
     * @param x the direction along the first axis
     * @param y the direction along the second axis
     * @param z the direction along the third axis
     */
    public record Direction(double x, double y, double z) {
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
    public static Direction direction(double fromX, double fromY, double fromZ, double toX, double toY,
                                       double toZ) {
        double length = Math.sqrt((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY)
                + (toZ - fromZ) * (toZ - fromZ));
        if (length <= 0.0) {
            return new Direction(0.0, 0.0, 0.0);
        }
        return new Direction((toX - fromX) / length, (toY - fromY) / length, (toZ - fromZ) / length);
    }

    /**
     * The turns of the arm of the player that point the bore of the weapon at the place the
     * player looks at.
     *
     * <p>The game of blocks turns the joint of the arm of the player around the first axis of the
     * model of the player and then around the second axis, so the turn of the arm around the first
     * axis is the turn of the head around the side axis and the turn of the arm around the second
     * axis is the turn of the head around the vertical axis. The turn of the head around the body
     * of the player is enough, because the head of the player may be turned away from the body.
     *
     * <p>The arm of the player is raised to aim: the turn of the arm around the first axis is a
     * quarter of a turn less than the turn of the head around the side axis, so that the arm of
     * the player points forward while the player looks ahead and follows the head of the player
     * when the head is tilted.
     *
     * @param look the look of the player
     * @return the turns of the arm of the player
     */
    public static ArmTurn aim(Look look) {
        return new ArmTurn(look.headPitch() - 90.0, look.headYaw());
    }

    /**
     * The place of the mouth of the weapon in the world, in the third person.
     *
     * @param x the place of the player along the first axis
     * @param y the place of the player along the second axis
     * @param z the place of the player along the third axis
     * @param look the look of the player
     * @return the place of the mouth of the weapon
     */
    public static Point muzzle(double x, double y, double z, Look look) {
        var pose = com.github.giannigr.racmc.item.weapon.HandPoseSnapshot.standing(
                new com.github.giannigr.racmc.item.weapon.WeaponDefinition.Vector(x, y, z),
                look.bodyYaw(), look.headYaw(), look.headPitch(), false, false, 1);
        return muzzle(pose);
    }

    public static Point muzzle(com.github.giannigr.racmc.item.weapon.HandPoseSnapshot pose) {
        var point = com.github.giannigr.racmc.item.weapon.HandTransform.muzzle(
                com.github.giannigr.racmc.item.weapon.Rac1BlasterDefinition.INSTANCE, pose);
        return new Point(point.x(), point.y(), point.z());
    }

    /**
     * The place of the socket of the weapon in the world, in the third person.
     *
     * <p>The socket is the place the hand of the player holds the weapon at.
     *
     * @param x the place of the player along the first axis
     * @param y the place of the player along the second axis
     * @param z the place of the player along the third axis
     * @param look the look of the player
     * @return the place of the socket of the weapon
     */
    public static Point grip(double x, double y, double z, Look look) {
        return toWorld(x, y, z, inModel(new double[] { 0.0, 0.0, 0.0 }, aim(look)),
                look.bodyYaw());
    }

    /**
     * The direction the bore of the weapon points at, in the world, in the third person.
     *
     * @param look the look of the player
     * @return the direction of the bore of the weapon
     */
    public static Direction barrel(Look look) {
        double[] model = inModelDirection(new double[] { 0.0, 0.0, 1.0 }, aim(look));
        double[] world = toWorldDirection(model, look.bodyYaw());
        double length = Math.sqrt(world[0] * world[0] + world[1] * world[1] + world[2] * world[2]);
        if (length <= 0.0) {
            return new Direction(0.0, 0.0, 0.0);
        }
        return new Direction(world[0] / length, world[1] / length, world[2] / length);
    }

    /**
     * Moves a place of the mesh of the weapon into the frame of the model of the player.
     *
     * <p>The chain is the chain of the game of blocks: the turn of the model of the item, the
     * place of the item in the joint of the arm, the turn of the joint of the arm, the turn of
     * the arm of the player and the place of the joint of the arm.
     *
     * @param mesh the place in the frame of the mesh of the weapon
     * @param arm the turns of the arm of the player
     * @return the place in the frame of the model of the player
     */
    public static double[] inModel(double[] mesh, ArmTurn arm) {
        var pose = HandPoseSnapshot.standing(new Vector(0, 0, 0), 0, arm.y(), arm.x() + 90, false, false, 1);
        var point = HandTransform.modelPoint(Rac1BlasterDefinition.INSTANCE, pose, new Vector(mesh[0], mesh[1], mesh[2]));
        return new double[]{point.x(), point.y(), point.z()};
    }

    /**
     * Moves a direction of the mesh of the weapon into the frame of the model of the player.
     *
     * @param mesh the direction in the frame of the mesh of the weapon
     * @param arm the turns of the arm of the player
     * @return the direction in the frame of the model of the player
     */
    public static double[] inModelDirection(double[] mesh, ArmTurn arm) {
        var point = inModel(mesh, arm);
        var origin = inModel(new double[]{0, 0, 0}, arm);
        return new double[]{point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]};
    }

    /**
     * Moves a place of the model of the player into the world.
     *
     * @param x the place of the player along the first axis
     * @param y the place of the player along the second axis
     * @param z the place of the player along the third axis
     * @param model the place in the frame of the model of the player
     * @param bodyYaw the turn of the body of the player around the vertical axis, in degrees
     * @return the place in the world
     */
    public static Point toWorld(double x, double y, double z, double[] model, double bodyYaw) {
        var pose = HandPoseSnapshot.standing(new Vector(x, y, z), bodyYaw, 0, 0, false, false, 1);
        var world = HandTransform.worldPoint(pose, new Vector(model[0], model[1], model[2]));
        return new Point(world.x(), world.y(), world.z());
    }

    /**
     * Moves a direction of the model of the player into the world.
     *
     * @param model the direction in the frame of the model of the player
     * @param bodyYaw the turn of the body of the player around the vertical axis, in degrees
     * @return the direction in the world
     */
    public static double[] toWorldDirection(double[] model, double bodyYaw) {
        var point = toWorld(0, 0, 0, model, bodyYaw);
        var origin = toWorld(0, 0, 0, new double[]{0, 0, 0}, bodyYaw);
        return new double[]{point.x() - origin.x(), point.y() - origin.y(), point.z() - origin.z()};
    }
}
