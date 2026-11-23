package com.github.giannigr.racmc.client.bomb;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Ordinary public helper: its callers run as transformed vanilla renderer methods. */
public final class BombGloveRenderScope implements AutoCloseable {
    private final AbstractClientPlayer player;
    private final float partial;
    private final Matrix4f entityFrame;
    private final MultiBufferSource buffers;
    private ModelPart armorArm;
    private PartPose original;

    public BombGloveRenderScope(AbstractClientPlayer player, float partial, Matrix4f entityFrame,
                                MultiBufferSource buffers) {
        this.player = player;
        this.partial = partial;
        this.entityFrame = entityFrame;
        this.buffers = buffers;
    }

    public AbstractClientPlayer player() { return player; }
    public float partial() { return partial; }
    public Matrix4f entityFrame() { return entityFrame; }
    public MultiBufferSource buffers() { return buffers; }

    public void rememberArmorArm(ModelPart arm) {
        if (armorArm != null) return;
        armorArm = arm;
        original = arm.storePose();
    }

    @Override public void close() {
        if (armorArm == null) return;
        // Do not call loadPose: it also changes the scale, which this scope does not own.
        armorArm.setPos(original.x, original.y, original.z);
        armorArm.setRotation(original.xRot, original.yRot, original.zRot);
        armorArm = null;
        original = null;
    }

    public static final class SkinMask implements AutoCloseable {
        private final ModelPart arm;
        private final ModelPart sleeve;
        private final boolean skipArm;
        private final boolean skipSleeve;

        public SkinMask(ModelPart arm, ModelPart sleeve) {
            this.arm = arm;
            this.sleeve = sleeve;
            skipArm = arm.skipDraw;
            skipSleeve = sleeve.skipDraw;
            arm.skipDraw = true;
            sleeve.skipDraw = true;
        }

        @Override public void close() {
            arm.skipDraw = skipArm;
            sleeve.skipDraw = skipSleeve;
        }
    }

    public static final class PoseFrame implements AutoCloseable {
        private final PoseStack poses;

        public PoseFrame(PoseStack poses) {
            this.poses = poses;
            poses.pushPose();
        }

        @Override public void close() {
            poses.popPose();
        }
    }
}
