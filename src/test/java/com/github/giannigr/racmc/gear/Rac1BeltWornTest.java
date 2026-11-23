package com.github.giannigr.racmc.gear;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;

class Rac1BeltWornTest {
    @Test void nativeTrackedBitIsRegisteredNonpersistentAndScopedToItsEntityHolder() {
        assertSame(Rac1BeltWorn.BOLT_GRABBER, NeoForgeRegistries.ATTACHMENT_TYPES.get(Rac1BeltWorn.ID));
        class ProbeHolder extends AttachmentHolder {
            int syncs;
            @Override public void syncData(AttachmentType<?> type) { syncs++; }
        }
        var original = new ProbeHolder();
        var replacement = new ProbeHolder();
        assertNull(original.getExistingDataOrNull(Rac1BeltWorn.BOLT_GRABBER));
        original.setData(Rac1BeltWorn.BOLT_GRABBER, true);
        assertEquals(Boolean.TRUE, original.getExistingDataOrNull(Rac1BeltWorn.BOLT_GRABBER));
        var nbt = original.serializeAttachments(RegistryAccess.EMPTY);
        assertTrue(nbt == null || !nbt.contains(Rac1BeltWorn.ID.toString()));
        assertNull(replacement.getExistingDataOrNull(Rac1BeltWorn.BOLT_GRABBER));
        original.removeData(Rac1BeltWorn.BOLT_GRABBER);
        assertNull(original.getExistingDataOrNull(Rac1BeltWorn.BOLT_GRABBER));
        assertEquals(2, original.syncs);
        assertEquals(0, replacement.syncs);
    }
}
