package com.github.giannigr.racmc.item.weapon;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.*;
import com.mojang.authlib.properties.Property;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RangedSkinTest {
    private static final class LocalService implements MinecraftSessionService {
        int unpacked;
        public Property getPackedTextures(GameProfile profile) {
            return profile.getProperties().get("textures").stream().findFirst().orElse(null);
        }
        public MinecraftProfileTextures unpackTextures(Property property) {
            unpacked++;
            if (property.value().equals("invalid")) throw new IllegalArgumentException("Synthetic invalid metadata");
            return property.value().equals("none") ? MinecraftProfileTextures.EMPTY
                    : new MinecraftProfileTextures(new MinecraftProfileTexture(
                    "https://textures.minecraft.net/texture/synthetic", Map.of("model", property.value())),
                    null, null, SignatureState.SIGNED);
        }
        public void joinServer(UUID id, String token, String server) { fail("No login/network calls"); }
        public com.mojang.authlib.yggdrasil.ProfileResult hasJoinedServer(String name, String server, InetAddress address) {
            throw new AssertionError("No profile/network calls");
        }
        public com.mojang.authlib.yggdrasil.ProfileResult fetchProfile(UUID id, boolean secure) {
            throw new AssertionError("No profile/network calls");
        }
        public String getSecurePropertyValue(Property property) { return property.value(); }
    }
    @Test void defaultModelMatchesActualPinnedVanillaUuidSkinSelection() {
        for (int i = 0; i < 54; i++) {
            var id = new UUID(0, i);
            assertEquals(net.minecraft.client.resources.DefaultPlayerSkin.get(id).model()
                    == net.minecraft.client.resources.PlayerSkin.Model.SLIM, RangedSkin.defaultSlim(id));
        }
    }
    @Test void profileMetadataIsLocalCachedAndChangesDoNotKeepAnOldModel() {
        var service = new LocalService();
        var profile = new GameProfile(new UUID(0, 14), "synthetic-wide-default");
        assertFalse(RangedSkin.of(profile, service));
        profile.getProperties().put("textures", new Property("textures", "slim"));
        assertTrue(RangedSkin.of(profile, service));
        assertTrue(RangedSkin.of(profile, service));
        assertEquals(1, service.unpacked);
        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", "unknown"));
        assertFalse(RangedSkin.of(profile, service));
        assertEquals(2, service.unpacked);
    }
    @Test void missingSkinAndInvalidLoadUseSameUuidFallbackNotAlwaysWide() {
        var service = new LocalService();
        for (int i : new int[]{3, 14}) {
            var profile = new GameProfile(new UUID(0, i), "synthetic-default");
            assertEquals(RangedSkin.defaultSlim(profile.getId()), RangedSkin.of(profile, null));
            for (String value : new String[]{"none", "invalid"}) {
                profile.getProperties().removeAll("textures");
                profile.getProperties().put("textures", new Property("textures", value));
                assertEquals(RangedSkin.defaultSlim(profile.getId()), RangedSkin.of(profile, service));
            }
        }
    }
}
