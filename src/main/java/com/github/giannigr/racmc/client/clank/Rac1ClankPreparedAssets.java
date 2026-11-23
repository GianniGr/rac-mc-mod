package com.github.giannigr.racmc.client.clank;

import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets;
import com.github.giannigr.racmc.asset.clank.Rac1ClankAssets.Role;
import com.github.giannigr.racmc.clank.Rac1ClankEquipment.Module;
import com.github.giannigr.racmc.client.Rac1ImportedItemPose;
import java.io.IOException;
import java.util.*;
import java.util.function.ObjLongConsumer;

/**
 * CPU-only, consumer-owned preparation for a future Clank resource-generation handoff.
 * Prepared off-thread under the portable verified-image lease before client publication.
 * No files, media, GPU, Minecraft singleton, global caches, item grants or shared lifecycle.
 */
public final class Rac1ClankPreparedAssets {
    public enum Stage { MODEL_COPIES, IDLE_POSES, ITEM_FITS }
    public record Item(Rac1AnimatedAsset body,Rac1AnimatedAsset pack,
                       Rac1ImportedItemPose.Sample bodyPose,Rac1ImportedItemPose.Sample packPose,
                       Rac1ClankItemPose.Fit fit) {}
    private final String discKey;
    private final long generation;
    private final Map<Role,Rac1AnimatedAsset> models;
    private final Map<Module,Item> items;
    private Rac1ClankPreparedAssets(String key,long generation,Map<Role,Rac1AnimatedAsset> models,Map<Module,Item> items) {
        discKey=key;this.generation=generation;this.models=Map.copyOf(models);this.items=Map.copyOf(items);
    }
    public static Rac1ClankPreparedAssets prepare(Rac1ClankAssets source,long generation) throws IOException {
        return prepare(source,generation,(stage,nanos)->{});
    }
    public static Rac1ClankPreparedAssets prepare(Rac1ClankAssets source,long generation,
                                                 ObjLongConsumer<Stage> timings) throws IOException {
        if(generation<0)throw new IllegalArgumentException("Invalid Clank resource generation");
        Objects.requireNonNull(source);Objects.requireNonNull(timings);
        long start=System.nanoTime();
        var models=new EnumMap<Role,Rac1AnimatedAsset>(Role.class);
        for(var role:Role.values())models.put(role,source.copyModel(role));
        timings.accept(Stage.MODEL_COPIES,System.nanoTime()-start);
        start=System.nanoTime();
        var poses=new EnumMap<Role,Rac1ImportedItemPose.Sample>(Role.class);
        for(var role:Role.values())poses.put(role,Rac1ImportedItemPose.sample(models.get(role),1,0));
        timings.accept(Stage.IDLE_POSES,System.nanoTime()-start);
        start=System.nanoTime();
        var items=new EnumMap<Module,Item>(Module.class);
        for(var module:List.of(Module.HELI,Module.THRUSTER)) {
            var role=Rac1ClankItemPose.packRole(module);
            var body=poses.get(Role.CLANK);var pack=poses.get(role);
            items.put(module,new Item(models.get(Role.CLANK),models.get(role),body,pack,
                    Rac1ClankItemPose.fit(body.positions(),pack.positions())));
        }
        timings.accept(Stage.ITEM_FITS,System.nanoTime()-start);
        return new Rac1ClankPreparedAssets(source.discKey(),generation,models,items);
    }
    /** These detached mutable model arrays belong to ONE consumer generation, never a shared cache. */
    public Rac1AnimatedAsset model(Role role){return models.get(role);}
    public Item item(Module module){return items.get(module);}
    public String discKey(){return discKey;}
    public long generation(){return generation;}
    /** Publication must also respect the future lease's validity, not just a cache key comparison. */
    public boolean matches(String key,long generation){return this.generation==generation&&discKey.equals(key);}
}
