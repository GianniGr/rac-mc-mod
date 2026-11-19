package com.github.giannigr.racmc.clank;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;

/** Native hitbox/voxel/entity probes. No block-center height guesses or alternate collision body. */
public final class Rac1ClankNativeCollision {
    private Rac1ClankNativeCollision() {}
    public static boolean compatible(Player player) {
        return player.isAlive()&&!player.isRemoved()&&!player.isSpectator()&&!player.isPassenger()
                &&!player.isInWater()&&!player.isInLava()&&!player.isSwimming()
                &&!player.level().containsAnyLiquid(player.getBoundingBox().deflate(1e-4))
                &&!player.isInFluidType()&&!player.isFallFlying()&&!player.getAbilities().flying
                &&!player.onClimbable()&&!player.isSleeping()&&!player.isAutoSpinAttack()
                &&!player.hasEffect(MobEffects.LEVITATION);
    }
    public static Rac1ClankMotion.Observation observe(Player player,Rac1ClankMotion.Vector initialVelocity) {
        var box=player.getBoundingBox();
        Vec3 downward=new Vec3(0,-32,0);
        var entities=player.level().getEntityCollisions(player,box.expandTowards(downward));
        double ground=-Entity.collideBoundingBox(player,downward,box,player.level(),entities).y;
        double yaw=Math.toRadians(player.getYRot());
        Vec3 ahead=new Vec3(-Math.sin(yaw)*2.5,0,Math.cos(yaw)*2.5);
        boolean blocked=!player.level().noCollision(player,box.expandTowards(ahead).deflate(1e-6));
        return new Rac1ClankMotion.Observation(ground<1e-5,Math.max(0,ground),blocked,
                compatible(player),player.hurtTime>0,2,initialVelocity);
    }
    public static Vec3 minecraftDelta(Rac1ClankMotion.Vector velocity) {
        return new Vec3(velocity.x()*3,velocity.y()*3,velocity.z()*3);
    }
    public static Rac1ClankMotion.Vector sourceVelocity(Vec3 mcDelta) {
        double length=mcDelta.length();
        Vec3 bounded=length>1.29?mcDelta.scale(1.29/length):mcDelta;
        return new Rac1ClankMotion.Vector(bounded.x/3,bounded.y/3,bounded.z/3);
    }
}
