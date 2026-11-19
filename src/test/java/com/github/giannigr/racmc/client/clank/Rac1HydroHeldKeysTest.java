package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1ClankMotion.Vector;
import com.github.giannigr.racmc.clank.Rac1HydroMotion;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import org.junit.jupiter.api.Test;

class Rac1HydroHeldKeysTest {
    private static ToggleKeyMapping mapping(boolean toggle) {
        return new ToggleKeyMapping("racmc.hydro.test."+UUID.randomUUID(),73,"racmc.test",()->toggle);
    }
    @Test void actualNativeHoldAndToggleMappingsBothStopHydroOnPhysicalReleaseWithoutChangingVanillaLatch() {
        for(boolean toggle:new boolean[]{false,true}) {
            var binding=mapping(toggle);Set<InputConstants.Key> physical=new HashSet<>();
            physical.add(binding.getKey());binding.setDown(true);
            assertTrue(Rac1HydroHeldKeys.down(binding,true,physical::contains));
            physical.clear();binding.setDown(false);
            assertFalse(Rac1HydroHeldKeys.down(binding,true,physical::contains));
            assertEquals(toggle,binding.isDown(),"Hydro must not reset native toggle state");
        }
    }
    @Test void remappedKeyMouseAndScanBindingsAreResolvedFreshNotOriginalControlOrOldBinding() {
        KeyMapping binding=mapping(true);Set<InputConstants.Key> physical=new HashSet<>();
        physical.add(InputConstants.Type.KEYSYM.getOrCreate(341)); // Old Ctrl is not remapped I.
        assertFalse(Rac1HydroHeldKeys.down(binding,true,physical::contains));
        physical.add(binding.getKey());assertTrue(Rac1HydroHeldKeys.down(binding,true,physical::contains));
        for(var next:new InputConstants.Key[]{InputConstants.Type.KEYSYM.getOrCreate(74),
                InputConstants.Type.MOUSE.getOrCreate(1),InputConstants.Type.SCANCODE.getOrCreate(23)}) {
            binding.setKey(next);
            assertFalse(Rac1HydroHeldKeys.down(binding,true,physical::contains),"Old physical binding leaked");
            physical.add(next);assertTrue(Rac1HydroHeldKeys.down(binding,true,physical::contains));
            physical.clear();assertFalse(Rac1HydroHeldKeys.down(binding,true,physical::contains));
        }
        binding.setKey(InputConstants.UNKNOWN);
        assertFalse(Rac1HydroHeldKeys.down(binding,true,key->{fail("Unbound key queried");return true;}));
    }
    @Test void uiOrLostFocusReleasesWithoutReadingDeviceOrMutatingMappingAndNewRemapIsNotSticky() {
        var binding=mapping(true);binding.setDown(true);
        assertFalse(Rac1HydroHeldKeys.down(binding,false,key->{fail("UI/focus must not poll");return true;}));
        assertTrue(binding.isDown());
        assertFalse(Rac1HydroHeldKeys.down(binding,true,key->false));
        assertTrue(binding.isDown());
        binding.setKey(InputConstants.Type.KEYSYM.getOrCreate(75));
        assertFalse(Rac1HydroHeldKeys.down(binding,true,key->key.getValue()==73));
    }
    @Test void releasedToggleThrustCoastsRatherThanStaysPoweredAndSneakReleaseIsNotLatched() {
        var sprint=mapping(true);var sneak=mapping(true);sprint.setDown(true);sneak.setDown(true);
        var m=new Rac1HydroMotion();
        for(int i=0;i<33;i++) {
            var s=m.step(new Rac1HydroMotion.Input(Rac1HydroHeldKeys.down(sprint,true,key->true),
                    false,false,0,0,0),Vector.ZERO,false,false);
            m.resolved(s.displacement());
        }
        sprint.setDown(false);sneak.setDown(false);
        var release=new Rac1HydroMotion.Input(Rac1HydroHeldKeys.down(sprint,true,key->false),false,
                Rac1HydroHeldKeys.down(sneak,true,key->false),0,0,0);
        var s=m.step(release,Vector.ZERO,false,false);m.resolved(s.displacement());
        assertEquals(Rac1HydroMotion.Stage.DRIFT,m.state().stage());
        assertEquals(7-8.0/60,m.velocity().length(),1e-9);
        assertTrue(sprint.isDown()&&sneak.isDown(),"Vanilla toggle behavior must remain unchanged");
    }
}
