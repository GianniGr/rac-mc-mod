package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.sound.WrenchMeleeSignal;
import java.util.Arrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class WrenchMeleeAudioTest {
    @Test void exactPositivePrimaryPostAndAcceptedReturnAreBothRequired() {
        Object primary = new Object(), directSource = new Object();
        var witness = new WrenchMeleeAudio.Witness(primary, directSource);
        witness.incoming(primary, directSource);
        witness.incoming(new Object(), directSource);
        witness.record(new Object(), directSource, 5);
        witness.record(primary, new Object(), 5);
        witness.record(primary, directSource, 0);
        witness.record(primary, directSource, -1);
        witness.record(primary, directSource, Float.NaN);
        assertFalse(witness.confirmed(true));
        witness.record(primary, directSource, 1);
        assertFalse(witness.confirmed(false));
        assertTrue(witness.confirmed(true));
    }

    @Test void missingOrReentrantDamageAdmissionFallsBackInsteadOfGuessing() {
        Object primary = new Object(), directSource = new Object();
        var missing = new WrenchMeleeAudio.Witness(primary, directSource);
        missing.record(primary, directSource, 1);
        assertFalse(missing.confirmed(true));
        var nested = new WrenchMeleeAudio.Witness(primary, directSource);
        nested.incoming(primary, directSource);
        nested.incoming(primary, directSource);
        nested.record(primary, directSource, 1);
        assertFalse(nested.confirmed(true));
    }

    @Test void mappingKeepsOneExplicitAlternateClassAndOnlyDirectSuccessFeedback() {
        assertEquals(WrenchMeleeSignal.Variant.ALTERNATE, WrenchMeleeAudio.variant(EntityType.IRON_GOLEM));
        for (var type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON,
                EntityType.SNOW_GOLEM, EntityType.PLAYER}) {
            assertEquals(WrenchMeleeSignal.Variant.DEFAULT, WrenchMeleeAudio.variant(type));
        }
        assertTrue(WrenchMeleeAudio.directFeedback(SoundEvents.PLAYER_ATTACK_STRONG));
        assertTrue(WrenchMeleeAudio.directFeedback(SoundEvents.PLAYER_ATTACK_WEAK));
        assertFalse(WrenchMeleeAudio.directFeedback(SoundEvents.PLAYER_ATTACK_NODAMAGE));
        assertFalse(WrenchMeleeAudio.directFeedback(SoundEvents.PLAYER_ATTACK_KNOCKBACK));
        assertFalse(WrenchMeleeAudio.directFeedback(SoundEvents.PLAYER_ATTACK_SWEEP));
        assertFalse(WrenchMeleeAudio.directFeedback(SoundEvents.ZOMBIE_HURT));
        assertFalse(WrenchMeleeAudio.directFeedback(SoundEvents.ZOMBIE_DEATH));
    }

    @Test void listenerSelectionIsStrictVanillaRadiusInTheSameLevelAndExcludesSourceWhenRequested() {
        assertTrue(WrenchMeleeAudio.eligibleListener(false, Level.OVERWORLD, Level.OVERWORLD, 255, 16));
        assertFalse(WrenchMeleeAudio.eligibleListener(false, Level.OVERWORLD, Level.OVERWORLD, 256, 16));
        assertFalse(WrenchMeleeAudio.eligibleListener(false, Level.NETHER, Level.OVERWORLD, 0, 16));
        assertFalse(WrenchMeleeAudio.eligibleListener(true, Level.OVERWORLD, Level.OVERWORLD, 0, 16));
    }

    @Test void exactNativeMixinsAreAppliedInTheNeoForgeTestRuntime() {
        assertTrue(Arrays.stream(Player.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("racmc$primaryHurt")));
        assertTrue(Arrays.stream(Player.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("racmc$directFeedback")));
        assertTrue(Arrays.stream(ServerLevel.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("racmc$captureOffer")));
        assertTrue(Arrays.stream(ServerLevel.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("racmc$perListener")));
    }
}
