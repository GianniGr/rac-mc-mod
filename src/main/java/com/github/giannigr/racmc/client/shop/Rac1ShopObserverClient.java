package com.github.giannigr.racmc.client.shop;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.asset.shop.Rac1ShopKioskPose;
import com.github.giannigr.racmc.block.Rac1VendorBlockEntity;
import com.github.giannigr.racmc.shop.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Legacy channel sink plus shopper-local physical motion. Remote occupancy never animates a kiosk. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1ShopObserverClient {
    private static final class Local {
        final Rac1ShopLocalMotion motion=new Rac1ShopLocalMotion();
        Rac1ShopMotion.Stamp stamp;
        Rac1ShopKioskPose pose;
    }
    private static final Map<Rac1VendorBlockEntity,Local> poses=new WeakHashMap<>();
    private static Object world,assets;
    private static Rac1VendorAsset failedSource;
    private Rac1ShopObserverClient() {}
    private static void receive(Rac1ShopObserverNetwork.State state) {
        // Compatibility sink only: the owner superseded public observer animation.
    }
    private static void generation(Minecraft mc) {
        Object content=com.github.giannigr.racmc.client.RacMcClient.assets().content().orElse(null);
        if (world!=mc.level) { poses.clear(); world=mc.level; }
        if (assets!=content) { poses.clear(); failedSource=null; assets=content; }
    }
    public static Rac1VendorPose.Mesh pose(Rac1VendorBlockEntity vendor,Rac1VendorAsset source,float partialTick) {
        var mc=Minecraft.getInstance(); generation(mc);
        if (source==null || source==failedSource) return null;
        long profile=Rac1ShopFrameProfile.begin();
        try { return sample(vendor,source,partialTick); }
        catch (RuntimeException failure) {
            failedSource=source; poses.clear();
            org.slf4j.LoggerFactory.getLogger(Rac1ShopObserverClient.class)
                    .warn("Physical shop pose unavailable; preserving the normal closed vendor renderer",failure);
            return null;
        }
        finally { Rac1ShopFrameProfile.end(Rac1ShopFrameProfile.Stage.KIOSK_POSE,profile); }
    }
    public static boolean unfolded(Rac1VendorBlockEntity vendor) {
        var local=poses.get(vendor);
        return local!=null && local.stamp!=null && local.stamp.phase()==Rac1ShopMotion.Phase.OPEN;
    }
    private static Rac1VendorPose.Mesh sample(Rac1VendorBlockEntity vendor,Rac1VendorAsset source,float partialTick) {
        var mc=Minecraft.getInstance();
        var local=poses.get(vendor);
        if (local==null || local.stamp==null || source==null || vendor.getLevel()!=mc.level || mc.level==null) return null;
        var frame=local.motion.display(partialTick);
        if (frame.phase()==Rac1ShopMotion.Phase.CLOSED) return null;
        if (local.pose==null) local.pose=new Rac1ShopKioskPose(source.body());
        var evaluator=local.pose;
        Rac1ShopKioskPose.Pose pose;
        if (frame.phase()==Rac1ShopMotion.Phase.UNFOLD)
            pose=evaluator.stage(Rac1ShopKioskPose.Stage.UNFOLD,frame.seconds(),List.of());
        else if (frame.phase()==Rac1ShopMotion.Phase.FOLD) {
            float weight=Math.min(1,frame.seconds()*60/8);
            pose=evaluator.blend(3,0,4,Math.max(0,evaluator.endTime(4)-frame.seconds()*.5f),weight,List.of());
        } else if (frame.phase()==Rac1ShopMotion.Phase.OPEN && frame.seconds()*60<8)
            pose=evaluator.blend(2,evaluator.endTime(2),3,0,frame.seconds()*60/8,List.of());
        else pose=evaluator.stage(Rac1ShopKioskPose.Stage.OPEN,0,List.of());
        return new Rac1VendorPose.Mesh(pose.positions(),pose.normals());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Rac1ShopObserverNetwork.client(Rac1ShopObserverClient::receive);
        var mc=Minecraft.getInstance();
        generation(mc);
        if (mc.level==null) return;
        var target=mc.screen instanceof Rac1ShopPreviewScreen shop ? shop.animationTarget():null;
        Rac1ShopFrameProfile.phase(target==null ? "OBSERVER_IDLE":"LOADING");
        if (target!=null) poses.computeIfAbsent(target,key -> new Local());
        var entries=poses.entrySet().iterator();
        while (entries.hasNext()) {
            var entry=entries.next();
            var vendor=entry.getKey();
            if (vendor.isRemoved() || vendor.getLevel()!=mc.level
                    || !mc.level.hasChunkAt(vendor.getBlockPos())
                    || mc.level.getBlockEntity(vendor.getBlockPos())!=vendor) { entries.remove(); continue; }
            var local=entry.getValue();
            local.stamp=local.motion.tick(vendor==target);
            if (vendor==target || target==null) Rac1ShopFrameProfile.phase(local.stamp.phase(),vendor!=target);
            if (local.stamp.phase()==Rac1ShopMotion.Phase.CLOSED) entries.remove();
        }
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            for (var block:chunk.getBlockEntities().values()) if (block instanceof Rac1VendorBlockEntity vendor) {
                poses.remove(vendor);
            }
    }
}
