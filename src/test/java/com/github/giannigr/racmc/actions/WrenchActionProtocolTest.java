package com.github.giannigr.racmc.actions;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class WrenchActionProtocolTest {
    @Test void requestContainsOnlySerialAndIntentNotCoordinatesOrTargets() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            var request = new WrenchActionNetwork.Intent(42, WrenchActionState.Intent.THROW.ordinal());
            WrenchActionNetwork.Intent.CODEC.encode(buffer, request);
            assertEquals(10, buffer.readableBytes());
            assertEquals(request, WrenchActionNetwork.Intent.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void authoritativePresentationRoundTripsWithoutAssets() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            var view = new WrenchActionPresentation(5,
                    WrenchActionPresentation.Phase.DETACHED_BACK, 26, 6, 0.6, 0.5, 1, 0.8, false,
                    new ActionVector(4, 65, -2), 1.25, ActionRotation.IDENTITY);
            var snapshot = new WrenchActionNetwork.Snapshot(UUID.randomUUID(), 100, true, true, view);
            WrenchActionNetwork.Snapshot.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, WrenchActionNetwork.Snapshot.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void malformedPhaseCannotIndexOutsideEnum() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeLong(1);
            buffer.writeBoolean(true);
            buffer.writeBoolean(false);
            buffer.writeFloat(0);
            buffer.writeLong(1);
            buffer.writeByte(255);
            assertThrows(IllegalArgumentException.class,
                    () -> WrenchActionNetwork.Snapshot.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void nonFinitePresentationIsRejectedBeforeRenderTransforms() {
        assertThrows(IllegalArgumentException.class, () -> new WrenchActionPresentation(1,
                WrenchActionPresentation.Phase.SLAM_DESCENT, 43, 10, Double.NaN, 0, 1, 1, true,
                ActionVector.ZERO, 0, ActionRotation.IDENTITY));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
