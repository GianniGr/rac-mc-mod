package com.github.giannigr.racmc.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import static org.lwjgl.opengl.GL12.*;

/** Standalone HUD pages do not tile. Call on the render thread with their texture bound. */
public final class Rac1HudSampling {
    private Rac1HudSampling() {}
    public static void clampBoundTexture() {
        // Linear samples at u/v=0 or 1 must not borrow alpha from the opposite edge.
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
}
