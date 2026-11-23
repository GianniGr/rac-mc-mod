package com.github.giannigr.racmc.client.clank;

import static org.junit.jupiter.api.Assertions.*;
import com.github.giannigr.racmc.clank.Rac1HydroBoost;
import com.github.giannigr.racmc.clank.Rac1HydroWire;
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
    @Test void actualNativeHoldAndToggleMappingsExposePhysicalReleaseWithoutChangingVanillaLatch() {
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
    private static Rac1HydroBoost sample(Rac1HydroBoost state,Rac1HydroHeldKeys.Sample sample) {
        return state.sample(new Rac1HydroBoost.Controls(sample!=Rac1HydroHeldKeys.Sample.UNAVAILABLE,
                sample==Rac1HydroHeldKeys.Sample.DOWN,false,false,0,1,0),true);
    }
    @Test void nativeSprintMappingTogglesOncePerFreshPressNotReleaseOrRepeat() {
        for(boolean toggle:new boolean[]{false,true}) {
            var mapping=mapping(toggle);var binding=new Rac1HydroHeldKeys.Sprint();mapping.setDown(true);
            var state=sample(Rac1HydroBoost.BLOCKED,binding.sample(mapping,true,key->true));
            assertFalse(state.armed(),"Held across entry must not engage");
            state=sample(state,binding.sample(mapping,true,key->false));
            state=sample(state,binding.sample(mapping,true,key->true));
            assertTrue(state.armed());
            for(int i=0;i<10;i++)state=sample(state,binding.sample(mapping,true,key->true));
            assertTrue(state.armed(),"Repeat/hold toggled off");
            state=sample(state,binding.sample(mapping,true,key->false));
            assertTrue(state.armed(),"Physical release disarmed");
            state=sample(state,binding.sample(mapping,true,key->true));
            assertFalse(state.armed(),"Second physical press failed to disarm");
            assertTrue(mapping.isDown(),"Hydro must leave the native latch alone");
        }
    }
    @Test void reopeningGameplayAndRemappingWhileHeldRequireReleaseBeforeNewPress() {
        var mapping=mapping(true);var binding=new Rac1HydroHeldKeys.Sprint();
        var state=sample(Rac1HydroBoost.BLOCKED,binding.sample(mapping,true,key->false));
        state=sample(state,binding.sample(mapping,false,key->{fail("No device poll out of gameplay");return true;}));
        assertFalse(sample(state,binding.sample(mapping,true,key->true)).armed());
        state=sample(state,binding.sample(mapping,true,key->false));
        mapping.setKey(InputConstants.Type.MOUSE.getOrCreate(1));
        state=sample(state,binding.sample(mapping,true,key->{fail("Remap is not a press");return true;}));
        assertFalse(sample(state,binding.sample(mapping,true,key->true)).armed());
        state=sample(state,binding.sample(mapping,true,key->false));
        assertTrue(sample(state,binding.sample(mapping,true,key->true)).armed());
    }
    @Test void configuredControlThroughTheWireArmsThrustAndKeepsArmedOnForwardRelease() throws Exception {
        for (boolean toggle : new boolean[]{false, true}) {
            var mapping = mapping(toggle);
            mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(341)); // Default Left Ctrl.
            var sprint = new Rac1HydroHeldKeys.Sprint();
            var stream = new Rac1HydroWire.Stream(3);
            var boost = Rac1HydroBoost.BLOCKED;
            boolean[] down = {true, false, true, false, false, false, true, false, true};
            boolean[] forward = {true, true, true, true, false, true, true, true, true};
            boolean[] wet = {true, true, true, true, true, true, false, true, true};
            boolean[] armed = {false, false, true, true, true, true, false, false, true};
            for (int i = 0; i < down.length; i++) {
                boolean pressed = down[i];
                mapping.setDown(pressed);
                boolean vanilla = mapping.isDown();
                var sample = sprint.sample(mapping, wet[i], key -> pressed && key.getValue() == 341);
                var keys = new Rac1HydroBoost.Controls(sample != Rac1HydroHeldKeys.Sample.UNAVAILABLE,
                        sample == Rac1HydroHeldKeys.Sample.DOWN, false, false, 0, forward[i] ? 1 : 0, 0);
                var wire = Rac1HydroWire.input(Rac1HydroWire.encode(new Rac1HydroWire.Input(3, i, keys)));
                assertTrue(stream.offer(wire, i));
                for (var received : stream.consume(i)) boost = boost.sample(received, wet[i]);
                assertEquals(armed[i], boost.armed(), "sample " + i);
                assertEquals(armed[i] && forward[i], boost.drive(keys).thrust(), "sample " + i);
                assertEquals(vanilla, mapping.isDown(), "Hydro changed the vanilla binding");
            }
        }
    }
}
