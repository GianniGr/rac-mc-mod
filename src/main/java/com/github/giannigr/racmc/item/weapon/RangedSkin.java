package com.github.giannigr.racmc.item.weapon;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Vanilla profile-derived hand geometry, no client pose report or texture download.
 * Authlib unpackTextures is local signature/domain/metadata parsing; fetchProfile is never called.
 * Mirrors SkinManager/PlayerSkin.Model and the pinned eighteen-entry DefaultPlayerSkin fallback.
 */
public final class RangedSkin {
    private record Key(UUID player, Property property, MinecraftSessionService service) {}
    private static final Map<Key, Boolean> CACHE = new LinkedHashMap<>();
    private RangedSkin() {}
    public static boolean defaultSlim(UUID player) { return Math.floorMod(player.hashCode(), 18) < 9; }
    public static boolean server(Player player) {
        return player instanceof ServerPlayer server
                ? of(server.getGameProfile(), server.server.getSessionService()) : defaultSlim(player.getUUID());
    }
    public static synchronized boolean of(GameProfile profile, MinecraftSessionService service) {
        // GameTestServer deliberately has no authentication service; ordinary offline profiles
        // with no texture property also need only vanilla's deterministic UUID model.
        if (service == null || profile.getProperties().get("textures").isEmpty()) return defaultSlim(profile.getId());
        Property packed = service.getPackedTextures(profile);
        if (packed == null) return defaultSlim(profile.getId());
        var key = new Key(profile.getId(), packed, service);
        Boolean cached = CACHE.get(key);
        if (cached != null) return cached;
        boolean slim;
        try {
            MinecraftProfileTextures textures = service.unpackTextures(packed);
            slim = textures.skin() == null ? defaultSlim(profile.getId())
                    : "slim".equals(textures.skin().getMetadata("model"));
        } catch (RuntimeException unavailable) {
            // SkinManager's exceptional asynchronous load also retains its UUID fallback.
            slim = defaultSlim(profile.getId());
        }
        if (CACHE.size() >= 256) CACHE.clear();
        CACHE.put(key, slim);
        return slim;
    }
}
