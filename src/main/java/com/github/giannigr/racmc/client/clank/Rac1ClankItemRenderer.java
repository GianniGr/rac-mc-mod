package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.RacMcMod;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import com.github.giannigr.racmc.client.render.Rac1MeshRenderer;
import com.github.giannigr.racmc.item.Rac1ClankPackItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Static original idle601 + matching607/608, using the existing local Clank cache/texture leases. */
public final class Rac1ClankItemRenderer extends BlockEntityWithoutLevelRenderer {
    private record Prepared(Rac1AnimatedAsset body,Rac1AnimatedAsset pack,
            Rac1ImportedItemPose.Sample bodyPose,Rac1ImportedItemPose.Sample packPose,
            Rac1ClankItemPose.Fit fit) {}
    private static final Map<Module,Prepared> POSES=new EnumMap<>(Module.class);
    private static final Set<Object> FAILED=Collections.newSetFromMap(new IdentityHashMap<>());
    public Rac1ClankItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());
    }
    static void clear(){POSES.clear();FAILED.clear();}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,
            MultiBufferSource buffers,int light,int overlay) {
        if(!(stack.getItem() instanceof Rac1ClankPackItem item))return;
        Rac1ClankResources.update(true); // Lazy local import; never an ability/acquisition request.
        var role=Rac1ClankItemPose.packRole(item.module());
        var body=Rac1ClankResources.model(Role.CLANK);var pack=Rac1ClankResources.model(role);
        if(body==null||pack==null||FAILED.contains(body))return;
        try {
            var prepared=POSES.get(item.module());
            if(prepared==null||prepared.body()!=body||prepared.pack()!=pack) {
                var bodyPose=Rac1ImportedItemPose.sample(body,1,0);
                var packPose=Rac1ImportedItemPose.sample(pack,1,0);
                prepared=new Prepared(body,pack,bodyPose,packPose,
                        Rac1ClankItemPose.fit(bodyPose.positions(),packPose.positions()));
                POSES.put(item.module(),prepared);
            }
            pose.pushPose();
            try {
                pose.translate(.5,.5,.5); // Native item-cube corner, not the wearable torso socket.
                pose.mulPose(Axis.YP.rotationDegrees(-90)); // Original up stays item-up.
                var fit=prepared.fit();pose.scale(fit.scale(),fit.scale(),fit.scale());
                pose.translate(-fit.x(),-fit.y(),-fit.z());
                int itemLight=context==ItemDisplayContext.GUI?Rac1MeshRenderer.FULL_LIGHT:light;
                Rac1MeshRenderer.render(pose,buffers,body.mesh(),prepared.bodyPose().positions(),
                        prepared.bodyPose().normals(),Rac1ClankResources.textures(Role.CLANK),itemLight,true);
                Rac1MeshRenderer.render(pose,buffers,pack.mesh(),prepared.packPose().positions(),
                        prepared.packPose().normals(),Rac1ClankResources.textures(role),itemLight,true);
            } finally {pose.popPose();}
        }catch(RuntimeException failure) {
            FAILED.add(body);
            RacMcMod.LOGGER.warn("Local Clank Creative item could not be drawn: {}",failure.toString());
        }
    }
}
