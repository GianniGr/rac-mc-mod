package com.github.giannigr.racmc.client.pda;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.asset.pda.Rac1PdaRemoteAudio;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Only deliberate local close can retire two short original cues beyond the private screen. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,value=Dist.CLIENT)
public final class Rac1PdaClosingAudio {
    private record Closing(Rac1PdaRemoteAudio audio,BooleanSupplier valid,List<Rac1PdaOriginalSound> sounds,int age) {}
    private static Closing closing;
    private Rac1PdaClosingAudio() {}
    static void start(Rac1PdaRemoteAudio audio,BooleanSupplier valid) {
        clear();
        if (!valid.getAsBoolean()) return;
        var sounds=new ArrayList<Rac1PdaOriginalSound>();
        play(audio,Rac1PdaRemoteAudio.Role.BACK,sounds);
        closing=new Closing(audio,valid,sounds,0);
    }
    private static void play(Rac1PdaRemoteAudio audio,Rac1PdaRemoteAudio.Role role,List<Rac1PdaOriginalSound> sounds) {
        var sound=new Rac1PdaOriginalSound(audio.clips().get(role));
        sounds.add(sound); Minecraft.getInstance().getSoundManager().play(sound);
    }
    static void clear() {
        if (closing!=null) {
            var manager=Minecraft.getInstance().getSoundManager();
            for (var sound:closing.sounds) { sound.close(); manager.stop(sound); }
            closing=null;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var c=closing; if (c==null) return;
        if (!c.valid.getAsBoolean() || c.age>=40) { clear(); return; }
        int age=c.age+1;
        // Source waits8 reference frames. MC20Hz acknowledgement/lifetime step rounds to3ticks.
        if (age==3) play(c.audio,Rac1PdaRemoteAudio.Role.CLOSED,c.sounds);
        closing=new Closing(c.audio,c.valid,c.sounds,age);
    }
}
