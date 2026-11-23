package com.github.giannigr.racmc.client.clank;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Reads the current physical binding without consuming clicks or mutating vanilla toggle state. */
public final class Rac1HydroHeldKeys {
    @FunctionalInterface public interface PhysicalState {boolean down(InputConstants.Key key);}
    private Rac1HydroHeldKeys() {}
    public static boolean down(KeyMapping mapping,boolean activeGameplay) {
        return down(mapping,activeGameplay,Rac1HydroHeldKeys::physical);
    }
    /** Detached native mappings + a fake device state permit headless hold/toggle regression checks. */
    public static boolean down(KeyMapping mapping,boolean activeGameplay,PhysicalState physical) {
        if(!activeGameplay||!mapping.isConflictContextAndModifierActive())return false;
        var key=mapping.getKey(); // Resolve on every sample: remaps cannot leave an old key held.
        return key.getValue()>=0&&physical.down(key);
    }
    public enum Sample { UNAVAILABLE, UP, DOWN }
    /** A remap is not a press, even if the newly configured key is already physically down. */
    public static final class Sprint {
        private InputConstants.Key key;
        private KeyModifier modifier;
        public Sample sample(KeyMapping mapping,boolean active) {
            return sample(mapping,active,Rac1HydroHeldKeys::physical);
        }
        public Sample sample(KeyMapping mapping,boolean active,PhysicalState physical) {
            var next=mapping.getKey();var nextModifier=mapping.getKeyModifier();
            boolean changed=key!=null&&(!key.equals(next)||modifier!=nextModifier);
            key=next;modifier=nextModifier;
            if(changed||!active||key.getValue()<0||!mapping.isConflictContextAndModifierActive())return Sample.UNAVAILABLE;
            return physical.down(key)?Sample.DOWN:Sample.UP;
        }
    }
    private static boolean physical(InputConstants.Key key) {
        long window=Minecraft.getInstance().getWindow().getWindow();
        return switch(key.getType()) {
            case KEYSYM->key.getValue()>=GLFW.GLFW_KEY_SPACE&&key.getValue()<=GLFW.GLFW_KEY_LAST
                    &&InputConstants.isKeyDown(window,key.getValue());
            case MOUSE->key.getValue()<=GLFW.GLFW_MOUSE_BUTTON_LAST
                    &&GLFW.glfwGetMouseButton(window,key.getValue())==GLFW.GLFW_PRESS;
            case SCANCODE->scanDown(window,key.getValue());
        };
    }
    private static boolean scanDown(long window,int scanCode) {
        // GLFW queries by key token, not scan code. Resolve through the current native layout;
        // unknown/unbound scan codes fail closed. No key cache survives a remap/layout change.
        for(int key=GLFW.GLFW_KEY_SPACE;key<=GLFW.GLFW_KEY_LAST;key++)
            if(GLFW.glfwGetKeyScancode(key)==scanCode&&InputConstants.isKeyDown(window,key))return true;
        return false;
    }
}
