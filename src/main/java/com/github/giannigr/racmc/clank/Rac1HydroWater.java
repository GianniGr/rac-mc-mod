package com.github.giannigr.racmc.clank;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** MC adapter, not a claim that a fluid tag equals an original RaC swim group. */
public final class Rac1HydroWater {
    public enum Mode { DRY, WADING, SURFACE, SUBMERGED }
    public record Sample(Mode mode,boolean compatible,boolean propulsion,boolean lowAir) {}
    private Rac1HydroWater() {}
    public static Mode classify(boolean water,boolean eyes,boolean nativeSwim,boolean grounded,
                                double immersion,double floorDistance) {
        if(!water)return Mode.DRY;
        if(eyes)return Mode.SUBMERGED;
        if(!grounded&&floorDistance>.6&&(immersion>=.8||nativeSwim&&immersion>.2))return Mode.SURFACE;
        return Mode.WADING;
    }
    public static Sample observe(Player player) {
        var level=player.level();var box=player.getBoundingBox().deflate(1e-4);
        boolean water=false,lava=false,flow=false,bubbles=false;double top=box.minY;
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),
                BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            var fluid=level.getFluidState(pos);
            double surface=pos.getY()+fluid.getHeight(level,pos);
            if(surface<=box.minY)continue;
            if(fluid.is(FluidTags.LAVA))lava=true;
            if(!fluid.is(FluidTags.WATER))continue;
            water=true;top=Math.max(top,surface);
            flow|=fluid.getFlow(level,pos).lengthSqr()>1e-8;
            bubbles|=level.getBlockState(pos).is(Blocks.BUBBLE_COLUMN);
        }
        var eye=player.getEyePosition();var eyePos=BlockPos.containing(eye);
        var eyeFluid=level.getFluidState(eyePos);
        boolean eyes=eyeFluid.is(FluidTags.WATER)&&eye.y<eyePos.getY()+eyeFluid.getHeight(level,eyePos);
        var down=new Vec3(0,-32,0);
        double floor=-Entity.collideBoundingBox(player,down,box,level,
                level.getEntityCollisions(player,box.expandTowards(down))).y;
        var mode=classify(water,eyes,player.isSwimming(),floor<1e-4,top-box.minY,floor);
        boolean compatible=player.isAlive()&&!player.isRemoved()&&!player.isSpectator()&&!player.isPassenger()
                &&!player.getAbilities().flying&&!player.isFallFlying()&&!player.isSleeping()
                &&!player.isAutoSpinAttack()&&!player.onClimbable()&&!lava
                &&!player.hasEffect(MobEffects.LEVITATION);
        boolean propulsion=compatible&&mode==Mode.SUBMERGED&&player.hurtTime==0&&!flow&&!bubbles
                &&!player.hasEffect(MobEffects.DOLPHINS_GRACE)
                &&player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY)==0;
        return new Sample(mode,compatible,propulsion,player.getAirSupply()<player.getMaxAirSupply()*.15);
    }
}
