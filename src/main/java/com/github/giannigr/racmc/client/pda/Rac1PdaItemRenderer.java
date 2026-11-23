package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.asset.*;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import com.github.giannigr.racmc.client.render.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Original60032 icon in GUI, original619 mesh elsewhere; Minecraft fitting, not native grip parity. */
public final class Rac1PdaItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static Rac1PdaItemRenderer instance;
    private final Rac1ItemTextureLease<ResourceLocation> icon=Rac1ImportedItemDrawing.textures("pda_inventory");
    private final Rac1ItemTextureLease<ResourceLocation> mesh=Rac1ImportedItemDrawing.textures("pda_handheld");
    private Object sampled;
    private Rac1ImportedItemPose.Sample pose;
    private static final Rac1Mesh ICON=new Rac1Mesh(
            new float[]{-.5f,-.5f,0,.5f,-.5f,0,.5f,.5f,0,-.5f,.5f,0},
            new float[]{0,0,1,0,0,1,0,0,1,0,0,1},
            new float[]{0,1,1,1,1,0,0,0},new int[]{0,1,2,0,2,3},new int[]{0,0});

    public Rac1PdaItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());
        instance=this;
    }
    static void clearTextures() {
        if (instance==null) return;
        instance.icon.clear(); instance.mesh.clear(); instance.sampled=null; instance.pose=null;
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack poses,
                                       MultiBufferSource buffers,int light,int overlay) {
        var resources=Rac1PdaVisuals.resources();
        if (resources==null) {
            icon.clear(); mesh.clear(); sampled=null; pose=null;
            return; // Fail closed. Never compass/book/vendor/missing-texture stand-in artwork.
        }
        poses.pushPose();
        try {
            Rac1ImportedItemDrawing.restoreSocketOrigin(poses);
            if (context==ItemDisplayContext.GUI) {
                var locations=icon.get(resources,Rac1ImportedItemDrawing.epoch(),
                        new Rac1Texture[]{resources.icons().getFirst()}).toArray(ResourceLocation[]::new);
                Rac1MeshRenderer.render(poses,buffers,ICON,locations,Rac1MeshRenderer.FULL_LIGHT,false);
            } else {
                if (sampled!=resources) {
                    sampled=resources;
                    // Original Gadgets-page class preview is sequence1. Static fitting is an MC adaptation.
                    pose=Rac1ImportedItemPose.sample(resources.handheld(),1,0);
                }
                if (pose==null || pose.positions().length==0) return;
                var locations=mesh.get(resources,Rac1ImportedItemDrawing.epoch(),
                        resources.handheld().textures()).toArray(ResourceLocation[]::new);
                Rac1ImportedItemDrawing.fitGui(poses,pose.positions());
                if (buffers instanceof MultiBufferSource.BufferSource source)
                    Rac1MeshRenderer.render(poses,source,resources.handheld().mesh(),pose.positions(),
                            pose.normals(),locations,light);
            }
        } finally { poses.popPose(); }
    }
}
