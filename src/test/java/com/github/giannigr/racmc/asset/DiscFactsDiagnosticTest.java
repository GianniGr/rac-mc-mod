package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.iso.Rac1DiscValidator;
import com.github.giannigr.racmc.iso.Rac1IsoSelectionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The check of the facts of a real disc image that the demo of the Blaster relies on.
 *
 * <p>The demo of the Blaster uses the joint lists of the class of the hero, the sequences of the
 * hero and the sequences of the weapon. This check holds the numbers of those facts, so that a
 * change of the reader of the classes is noticed.
 *
 * <p>This test is opt-in and is skipped unless the path of a disc image is given on the command
 * line:
 *
 * <pre>
 * .\gradlew.bat test --rerun -Dracmc.isoImage=C:/path/to/the/disc.iso
 * </pre>
 *
 * <p>The image is only read. It is never copied, moved, changed or written to, and no part of it
 * ends up in the repository or in a report of the test run.
 */
@EnabledIfSystemProperty(named = "racmc.isoImage", matches = ".+")
class DiscFactsDiagnosticTest {

    /**
     * The data of the level of the disc image.
     *
     * @return the data of the level
     */
    private static Rac1LevelAssets level() throws Exception {
        String image = System.getProperty("racmc.isoImage");
        assertNotNull(image, "The path of the disc image must be given");
        Path path = Path.of(image);
        assertTrue(Files.isRegularFile(path), "The given path must be a regular file");
        Rac1DiscValidator validator = new Rac1DiscValidator();
        try (Rac1IsoSelectionResult outcome = validator.inspect(path)) {
            assertTrue(outcome.isSupported(), outcome.result().message());
            return Rac1LevelAssets.load(outcome.image(), Rac1AssetExtractor.LEVEL);
        }
    }

    @Test
    @DisplayName("The joint lists of the class of the hero name the arm of the weapon")
    void theJointListsOfTheHeroNameTheArmOfTheWeapon() throws Exception {
        Rac1LevelAssets assets = level();
        MobyClassReader reader = MobyClassReader.of(
                assets.classBlob(assets.coreIndex().mobyClass(0).offset()));
        List<MobyClassReader.JointList> lists = reader.readJointLists();

        assertEquals(31, lists.size(), "The class of the hero holds thirty-one joint lists");

        // The first list is the chain of the hand: it ends at the joint 56, and the second list
        // of that entry names the joints that follow it, the fingers of the hand.
        byte[] hand = lists.get(0).chain();
        assertEquals(56, hand[hand.length - 1] & 0xFF,
                "The chain of the hand does not end at the joint 56");
        byte[] fingers = lists.get(0).target();
        assertEquals(56, fingers[0] & 0xFF, "The fingers of the hand do not start at the joint 56");
        assertEquals(70, fingers[fingers.length - 1] & 0xFF,
                "The fingers of the hand do not end at the joint 70");

        // The twelfth list is the arm of the weapon: the game evaluates it for the layer of the
        // arm of the hero. It ends at the joint 52, the shoulder of the same arm as the chain of
        // the hand.
        byte[] arm = lists.get(12).chain();
        assertEquals(52, arm[arm.length - 1] & 0xFF,
                "The chain of the arm of the weapon does not end at the joint 52");

        // The other arm of the hero holds the joints 72 to 91, and it is not part of the chain of
        // the hand: the weapon of the hero is in the arm of the chain of the hand.
        for (byte joint : hand) {
            assertTrue((joint & 0xFF) < 72,
                    "The chain of the hand holds a joint of the other arm");
        }
    }

    @Test
    @DisplayName("The weapon of the hero holds the chain of its muzzle")
    void theWeaponOfTheHeroHoldsTheChainOfItsMuzzle() throws Exception {
        Rac1LevelAssets assets = level();
        MobyClassReader reader = MobyClassReader.of(assets.gadgetClass(168));
        List<MobyClassReader.JointList> lists = reader.readJointLists();

        assertTrue(lists.size() > 0, "The class of the Blaster has no joint list");
        byte[] muzzle = lists.get(0).chain();
        assertEquals(7, muzzle.length, "The chain of the muzzle of the Blaster holds seven joints");
        assertEquals(0, muzzle[0] & 0xFF, "The chain of the muzzle does not start at the root");
        assertEquals(16, muzzle[muzzle.length - 1] & 0xFF,
                "The chain of the muzzle does not end at the joint 16");
    }

    @Test
    @DisplayName("The hero of the game holds the stance of the weapon and the idle")
    void theHeroOfTheGameHoldsTheStanceOfTheWeaponAndTheIdle() throws Exception {
        Rac1AnimatedAsset asset = Rac1AssetExtractor.readAnimatedAsset(level(), 0);

        assertEquals(88, asset.animations().length, "The class of the hero holds 88 sequences");
        assertNotNull(sequence(asset, "ratchet_sequence_0"),
                "The hero of the game has no idle");
        assertNotNull(sequence(asset, "ratchet_sequence_56"),
                "The hero of the game has no stance for the weapon");

        // The idle of the hero is a second long, and the stance of the weapon is a third of a
        // second long. Both of them repeat themselves.
        Rac1Animation idle = sequence(asset, "ratchet_sequence_0");
        Rac1Animation stance = sequence(asset, "ratchet_sequence_56");
        assertEquals(11, idle.keyCount(), "The idle of the hero holds eleven keys");
        assertEquals(1.3333334f, idle.times()[idle.times().length - 1], 0.001f,
                "The idle of the hero lasts one and a third seconds");
        assertEquals(6, stance.keyCount(), "The stance of the weapon holds six keys");
        assertEquals(0.33333334f, stance.times()[stance.times().length - 1], 0.001f,
                "The stance of the weapon lasts a third of a second");
    }

    /**
     * The sequence of an asset with the given name.
     *
     * @param asset the asset
     * @param name the name of the sequence
     * @return the sequence, or {@code null} when the asset has no such sequence
     */
    private static Rac1Animation sequence(Rac1AnimatedAsset asset, String name) {
        for (Rac1Animation animation : asset.animations()) {
            if (animation.name().equals(name)) {
                return animation;
            }
        }
        return null;
    }
}
