package com.github.giannigr.racmc.client.actions;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.actions.WrenchActionPresentation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Scoped native-Steve pose hooks. Original channel calibration is supplied, not guessed here.
 * Third-person runs after setupAnim; first-person requires an explicit complete arm+item draw.
 */
@EventBusSubscriber(modid = RacMcConstants.MOD_ID, value = Dist.CLIENT)
public final class WrenchPlayerPoseHook {
    private static final Deque<Scope> SCOPES = new ArrayDeque<>();
    private static final Map<UUID, Transition> TRANSITIONS = new HashMap<>();
    private static PoseSource source;
    private static HandRenderer handRenderer;

    private WrenchPlayerPoseHook() {}

    public static Player actor() {
        Scope scope = SCOPES.peek();
        return scope == null ? null : scope.player;
    }

    public static float actorPartialTick() {
        Scope scope = SCOPES.peek();
        return scope == null ? 0 : scope.partialTick;
    }

    public static void install(PoseSource poses, HandRenderer hands) {
        restoreStaleScopes();
        TRANSITIONS.clear();
        source = poses;
        handRenderer = hands;
    }

    @SubscribeEvent
    public static void before(RenderPlayerEvent.Pre event) {
        if (source == null) return;
        PlayerModel<?> model = event.getRenderer().getModel();
        SCOPES.push(new Scope(model, event.getEntity(), event.getPartialTick(), save(model)));
    }

    /** Called only by the client-only PlayerModel tail Mixin, never by gameplay or packet code. */
    public static void afterSetup(PlayerModel<?> model, LivingEntity entity) {
        Scope scope = SCOPES.peek();
        if (source == null || scope == null || scope.model != model || scope.player != entity) return;
        WrenchActionPresentation action = WrenchActionClient.presentation(
                scope.player, scope.partialTick);
        if (action.phase() == WrenchActionPresentation.Phase.IDLE) {
            TRANSITIONS.remove(scope.player.getUUID());
            return;
        }
        Pose pose = source.sample(scope.player, action, scope.partialTick);
        if (pose == null) return;
        Transition start = TRANSITIONS.get(scope.player.getUUID());
        if (start == null || start.serial != action.serial()) {
            start = new Transition(action.serial(), new Pose(Part.from(model.body),
                    Part.from(model.rightArm), Part.from(model.leftArm)));
            TRANSITIONS.put(scope.player.getUUID(), start);
        }
        Part.blend(start.pose.body, pose.body, action.bodyBlendWeight()).apply(model.body);
        Part.blend(start.pose.rightArm, pose.rightArm, action.bodyBlendWeight()).apply(model.rightArm);
        Part.blend(start.pose.leftArm, pose.leftArm, action.bodyBlendWeight()).apply(model.leftArm);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    @SubscribeEvent
    public static void after(RenderPlayerEvent.Post event) {
        Scope scope = SCOPES.peek();
        if (scope != null && scope.player == event.getEntity()
                && scope.model == event.getRenderer().getModel()) {
            SCOPES.pop().restore();
        }
    }

    @SubscribeEvent
    public static void firstPerson(RenderHandEvent event) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || source == null || handRenderer == null
                ) return;
        WrenchActionPresentation action = WrenchActionClient.presentation(player);
        if (action.phase() != WrenchActionPresentation.Phase.IDLE
                && handRenderer.render(event, player, action, source)) {
            // Returning true promises the caller already drew the complete custom arm/item pass.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void nextTick(ClientTickEvent.Pre event) {
        // A canceled render or exceptional render without Post cannot leak model pivots forever.
        restoreStaleScopes();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) TRANSITIONS.clear();
        else TRANSITIONS.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    private static void restoreStaleScopes() {
        while (!SCOPES.isEmpty()) SCOPES.pop().restore();
    }

    private static SavedPart[] save(PlayerModel<?> model) {
        return new SavedPart[] {new SavedPart(model.body), new SavedPart(model.rightArm),
                new SavedPart(model.leftArm), new SavedPart(model.jacket),
                new SavedPart(model.rightSleeve), new SavedPart(model.leftSleeve)};
    }

    /** Absolute native model values: pixels for pivots, radians for rotations; no limb scaling. */
    public record Part(float x, float y, float z, float xRot, float yRot, float zRot) {
        public Part {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(xRot) || !Float.isFinite(yRot) || !Float.isFinite(zRot)) {
                throw new IllegalArgumentException("Non-finite Steve pose");
            }
        }

        private void apply(ModelPart part) {
            part.setPos(x, y, z);
            part.setRotation(xRot, yRot, zRot);
        }

        private static Part from(ModelPart part) {
            return new Part(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot);
        }

        private static Part blend(Part from, Part to, double weight) {
            float t = (float) Math.clamp(weight, 0, 1);
            var q = new org.joml.Quaternionf().rotationZYX(from.zRot, from.yRot, from.xRot)
                    .slerp(new org.joml.Quaternionf().rotationZYX(to.zRot, to.yRot, to.xRot), t);
            var euler = q.getEulerAnglesZYX(new org.joml.Vector3f());
            return new Part(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t, euler.x, euler.y, euler.z);
        }
    }

    public record Pose(Part body, Part rightArm, Part leftArm) {}

    @FunctionalInterface
    public interface PoseSource {
        Pose sample(Player player, WrenchActionPresentation action, float partialTick);
    }

    @FunctionalInterface
    public interface HandRenderer {
        boolean render(RenderHandEvent event, Player player, WrenchActionPresentation action,
                PoseSource poses);
    }

    private record Scope(PlayerModel<?> model, Player player, float partialTick, SavedPart[] saved) {
        void restore() {
            for (SavedPart part : saved) part.restore();
        }
    }

    private record Transition(long serial, Pose pose) {}

    private static final class SavedPart {
        private final ModelPart target;
        private final Part pose;
        private final float xScale;
        private final float yScale;
        private final float zScale;

        SavedPart(ModelPart target) {
            this.target = target;
            pose = new Part(target.x, target.y, target.z,
                    target.xRot, target.yRot, target.zRot);
            xScale = target.xScale;
            yScale = target.yScale;
            zScale = target.zScale;
        }

        void restore() {
            pose.apply(target);
            target.xScale = xScale;
            target.yScale = yScale;
            target.zScale = zScale;
        }
    }
}
