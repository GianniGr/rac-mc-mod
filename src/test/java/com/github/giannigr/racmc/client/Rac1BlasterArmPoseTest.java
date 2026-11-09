package com.github.giannigr.racmc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.ModSource;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the pose of the arm that holds the Blaster.
 *
 * <p>The pose of the aim is only the pose of the arm while the weapon really fires. A player
 * that only holds the weapon keeps the pose of the game of blocks for an item that is held, and
 * the other arm of the player is never turned.
 */
class Rac1BlasterArmPoseTest {

    @Test
    @DisplayName("The arm points at the place the player looks at while the weapon fires")
    void theArmPointsWhileTheWeaponFires() {
        assertTrue(Rac1BlasterArmPose.aimsWithMainHand(true, true, true));
    }

    @Test
    @DisplayName("The arm of the game of blocks holds the weapon that is only held")
    void theArmHoldsTheWeaponThatIsOnlyHeld() {
        assertFalse(Rac1BlasterArmPose.aimsWithMainHand(false, true, true),
                "the arm points at the place the player looks at without a shot");
        assertFalse(Rac1BlasterArmPose.aimsWithMainHand(true, false, true),
                "the arm points at the place the player looks at while the other hand fires");
        assertFalse(Rac1BlasterArmPose.aimsWithMainHand(true, true, false),
                "the arm points at the place the player looks at without the Blaster in the hand");
        assertFalse(Rac1BlasterArmPose.aimsWithMainHand(false, false, false));
    }

    @Test
    @DisplayName("The new pose of the arm is not a pose of two hands")
    void theNewPoseIsNotAPoseOfTwoHands() {
        assertEquals(Boolean.FALSE, Rac1BlasterArmParams.getArmPoseTransformer(0, boolean.class),
                "the other arm of the player is turned into the aim of the weapon");
    }

    @Test
    @DisplayName("The new pose of the arm has a transformer")
    void theNewPoseHasATransformer() {
        assertNotNull(Rac1BlasterArmParams.getArmPoseTransformer(1, IArmPoseTransformer.class));
    }

    @Test
    @DisplayName("The pose of the aim is only used while the weapon fires with the main hand")
    void theAimIsOnlyUsedWhileTheWeaponFires() {
        String body = ModSource.methodOf(
                ModSource.of("com/github/giannigr/racmc/client/RacMcClientRegistration.java"),
                "public HumanoidModel.ArmPose getArmPose(");
        assertTrue(body.contains("Rac1BlasterClient.isFiring"),
                "the pose of the aim does not ask whether the weapon fires");
        assertTrue(body.contains("InteractionHand.MAIN_HAND"),
                "the pose of the aim does not ask for the main hand");
        assertTrue(body.indexOf("Rac1BlasterClient.isFiring") < body.indexOf("Rac1BlasterArmPose.aim()"),
                "the pose of the aim is used before the weapon is asked for");
    }

    @Test
    @DisplayName("The other arm of the player keeps the pose of the game of blocks")
    void theOtherArmKeepsThePoseOfTheGameOfBlocks() {
        String body = ModSource.methodOf(
                ModSource.of("com/github/giannigr/racmc/client/RacMcClientRegistration.java"),
                "public HumanoidModel.ArmPose getArmPose(");
        assertTrue(body.contains("return null"),
                "the other arm of the player does not keep the pose of the game of blocks");
    }
}
