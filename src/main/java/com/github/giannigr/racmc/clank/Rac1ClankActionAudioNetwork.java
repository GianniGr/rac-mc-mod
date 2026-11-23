package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcConstants;
import com.github.giannigr.racmc.sound.Rac1ClankActionEvent;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Separate S2C-only presentation channel. Frozen movement/snapshot protocols unchanged. */
@EventBusSubscriber(modid=RacMcConstants.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class Rac1ClankActionAudioNetwork {
    private static Consumer<Rac1ClankActionEvent> receiver=value->{};
    private Rac1ClankActionAudioNetwork() {}
    public static void receiver(Consumer<Rac1ClankActionEvent> next){receiver=java.util.Objects.requireNonNull(next);}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("clank-action-audio-1").playToClient(Event.TYPE,Event.CODEC,
                (packet,context)->receiver.accept(packet.value()));
    }
    public record Event(Rac1ClankActionEvent value) implements CustomPacketPayload {
        public static final Type<Event> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(
                RacMcConstants.MOD_ID,"clank_reached_sound"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Event> CODEC=StreamCodec.of(
                (out,packet)->out.writeBytes(packet.value().encode()),in->{
                    byte[] bytes=new byte[Rac1ClankActionEvent.BYTES];in.readBytes(bytes);
                    try{return new Event(Rac1ClankActionEvent.decode(bytes));}
                    catch(IOException bad){throw new IllegalArgumentException(bad);}
                });
        @Override public Type<Event> type(){return TYPE;}
    }
}
