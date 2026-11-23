package com.github.giannigr.racmc.gear;

import static org.junit.jupiter.api.Assertions.*;

import com.github.giannigr.racmc.clank.Rac1ClankEquipment;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class Rac1GearNetworkTest {
    @Test void selfSnapshotAndConditionalBackRequestRoundTripWithoutPlayerIdentity() {
        var back = new Rac1ClankEquipment(true, true, false, true, Rac1ClankEquipment.Module.HELI);
        var snapshot = new Rac1GearNetwork.Snapshot(new Rac1GearState(3, 2, back, false, false, false));
        var request = new Rac1GearNetwork.EquipBack(3, 2, false);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            Rac1GearNetwork.Snapshot.CODEC.encode(buf, snapshot);
            assertEquals(24, buf.readableBytes());
            assertEquals(snapshot, Rac1GearNetwork.Snapshot.CODEC.decode(buf));
            assertFalse(buf.isReadable());
            Rac1GearNetwork.EquipBack.CODEC.encode(buf, request);
            assertEquals(17, buf.readableBytes());
            assertEquals(request, Rac1GearNetwork.EquipBack.CODEC.decode(buf));
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
    }

    @Test void wrongEpochOrRevisionFailsAtDecodeAndMalformedBackBytesAreRejected() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buf.writeLong(0).writeLong(0).writeBoolean(true);
            assertThrows(IllegalArgumentException.class, () -> Rac1GearNetwork.EquipBack.CODEC.decode(buf));
            buf.clear().writeLong(1).writeLong(-1).writeBoolean(true);
            assertThrows(IllegalArgumentException.class, () -> Rac1GearNetwork.EquipBack.CODEC.decode(buf));
            buf.clear().writeLong(1).writeLong(0).writeByte(2);
            assertThrows(IllegalArgumentException.class, () -> Rac1GearNetwork.EquipBack.CODEC.decode(buf));
            buf.clear().writeLong(1).writeLong(1).writeBytes(new byte[7]).writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> Rac1GearNetwork.Snapshot.CODEC.decode(buf));
            buf.clear().writeLong(1).writeLong(1).writeBytes(Rac1ClankEquipment.EMPTY.save()).writeByte(8);
            assertThrows(IllegalArgumentException.class, () -> Rac1GearNetwork.Snapshot.CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }
}
