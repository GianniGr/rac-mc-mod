package com.github.giannigr.racmc.client.actions;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/** Restores flat native model/wear parts; cape/armor root matrices live inside native push/pop. */
public final class WrenchModelPoseSnapshot {
    private record Saved(ModelPart part, float x, float y, float z, float rx, float ry, float rz,
            float sx, float sy, float sz) {
        Saved(ModelPart part) {
            this(part, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale);
        }
        void restore() {
            part.setPos(x, y, z);
            part.setRotation(rx, ry, rz);
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
        }
    }
    private final List<Saved> parts;
    private WrenchModelPoseSnapshot(List<Saved> parts) { this.parts = List.copyOf(parts); }
    public static WrenchModelPoseSnapshot capture(HumanoidModel<?> model) {
        List<Saved> parts = new ArrayList<>();
        for (ModelPart p : new ModelPart[] {model.head, model.hat, model.body, model.rightArm,
                model.leftArm, model.rightLeg, model.leftLeg}) parts.add(new Saved(p));
        if (model instanceof PlayerModel<?> player) {
            for (ModelPart p : new ModelPart[] {player.jacket, player.rightSleeve,
                    player.leftSleeve, player.rightPants, player.leftPants}) parts.add(new Saved(p));
        }
        return new WrenchModelPoseSnapshot(parts);
    }
    public void restore() { parts.forEach(Saved::restore); }
}
