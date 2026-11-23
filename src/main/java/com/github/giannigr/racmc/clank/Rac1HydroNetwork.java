package com.github.giannigr.racmc.clank;

import com.github.giannigr.racmc.RacMcConstants;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid=RacMcConstants.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class Rac1HydroNetwork {
    private static Consumer<Rac1HydroWire.Snapshot> receiver=value->{};
    private Rac1HydroNetwork() {}
    public static void receiver(Consumer<Rac1HydroWire.Snapshot> consumer){receiver=java.util.Objects.requireNonNull(consumer);}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("clank-hydro-test-2");
        registrar.playToServer(Input.TYPE,Input.CODEC,(packet,context)->{
            if(context.player() instanceof ServerPlayer player)Rac1HydroRuntime.input(player,packet.value());
        });
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,context)->receiver.accept(packet.value()));
    }
    public record Input(Rac1HydroWire.Input value) implements CustomPacketPayload {
        public static final Type<Input> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,"hydro_test_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=StreamCodec.of(
                (out,v)->out.writeByteArray(Rac1HydroWire.encode(v.value())),
                in->{try{return new Input(Rac1HydroWire.input(in.readByteArray(Rac1HydroWire.MAX_BYTES)));}
                    catch(IOException bad){throw new IllegalArgumentException(bad);}});
        @Override public Type<Input> type(){return TYPE;}
    }
    public record Snapshot(Rac1HydroWire.Snapshot value) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RacMcConstants.MOD_ID,"hydro_test_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
                (out,v)->out.writeByteArray(Rac1HydroWire.encode(v.value())),
                in->{try{return new Snapshot(Rac1HydroWire.snapshot(in.readByteArray(Rac1HydroWire.MAX_BYTES)));}
                    catch(IOException bad){throw new IllegalArgumentException(bad);}});
        @Override public Type<Snapshot> type(){return TYPE;}
    }
}
