package com.github.giannigr.racmc.item.weapon.bomb;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Small presentation-only contract shared by the throw authority and client renderers. */
public final class BombGloveTracking {
    private BombGloveTracking() {}

    public enum Phase {
        HIDDEN, READY, WINDUP, RELEASED, ABORTED;

        public boolean drawsHeld() { return this == READY || this == WINDUP; }
    }

    public record Key(UUID owner, ResourceLocation dimension, UUID epoch, long throwId) {
        public Key {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(epoch);
            if (throwId < 0) throw new IllegalArgumentException("Invalid Bomb Glove throw ID");
        }
    }

    public record Held(Key key, int actorId, long revision, Phase phase, long phaseTick) {
        public Held {
            Objects.requireNonNull(key);
            Objects.requireNonNull(phase);
            if (revision <= 0 || (key.throwId() == 0) != (phase == Phase.HIDDEN))
                throw new IllegalArgumentException("Invalid Bomb Glove held snapshot");
        }
    }

    /** Presence, not the native projectile spawn packet, authorizes the flight model. */
    public record Commit(Key key, long releaseTick) {
        public Commit {
            Objects.requireNonNull(key);
            if (key.throwId() <= 0) throw new IllegalArgumentException("Cannot commit a hidden throw");
        }

        CompoundTag encode() {
            var tag = new CompoundTag();
            tag.putUUID("Owner", key.owner());
            tag.putString("Dimension", key.dimension().toString());
            tag.putUUID("Epoch", key.epoch());
            tag.putLong("Throw", key.throwId());
            tag.putLong("Release", releaseTick);
            return tag;
        }

        static @Nullable Commit decode(CompoundTag tag) {
            if (!tag.hasUUID("Owner") || !tag.hasUUID("Epoch") || tag.getLong("Throw") <= 0
                    || !tag.contains("Release", Tag.TAG_LONG)) return null;
            var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            return dimension == null ? null : new Commit(new Key(tag.getUUID("Owner"), dimension,
                    tag.getUUID("Epoch"), tag.getLong("Throw")), tag.getLong("Release"));
        }
    }

    /** Pose only: not permission to draw a held bomb or to release anything. */
    public record VisualPhase(Phase phase, long tick) {}

    /** One reducer per observed ClientLevel identity. Never derives a release from native use. */
    public static final class View {
        private static final int MAX_OWNERS = 2048;
        private static final int MAX_EPOCHS = 8;
        private final ResourceLocation dimension;
        private final Map<UUID, Observation> observations = new HashMap<>();

        private static final class Observation {
            Held held;
            Held terminal;
            final Map<UUID, Commit> committed = new HashMap<>();
            boolean overflow;
        }

        public View(ResourceLocation dimension) { this.dimension = dimension; }

        private @Nullable Observation observation(Key key) {
            if (!dimension.equals(key.dimension())) return null;
            var seen = observations.get(key.owner());
            if (seen == null && observations.size() < MAX_OWNERS) {
                seen = new Observation();
                observations.put(key.owner(), seen);
            }
            return seen;
        }

        public void held(Held snapshot) {
            var seen = observation(snapshot.key());
            if (seen == null) return;
            if (seen.held == null || snapshot.revision() > seen.held.revision()) {
                seen.held = snapshot;
                if (snapshot.phase() == Phase.RELEASED || snapshot.phase() == Phase.ABORTED) {
                    seen.terminal = snapshot;
                }
            }
        }

        public void committed(Commit commit) {
            var key = commit.key();
            var seen = observation(key);
            if (seen == null) return;
            if (seen.committed.size() == MAX_EPOCHS && !seen.committed.containsKey(key.epoch())) {
                seen.overflow = true;
                return; // fail closed for held rendering; never evict a live suppression record
            }
            seen.committed.merge(key.epoch(), commit,
                    (a, b) -> a.key().throwId() >= b.key().throwId() ? a : b);
        }

        public @Nullable Held heldBomb(UUID owner, int actorId) {
            var seen = observations.get(owner);
            if (seen == null || seen.overflow) return null;
            var held = seen.held;
            if (held == null || held.actorId() != actorId || !held.phase().drawsHeld()) return null;
            var key = held.key();
            var commit = seen.committed.get(key.epoch());
            return commit == null || commit.key().throwId() < key.throwId() ? held : null;
        }

        public @Nullable VisualPhase visualPhase(UUID owner, int actorId) {
            var seen = observations.get(owner);
            if (seen == null || seen.held == null || seen.held.actorId() != actorId) return null;
            var held = seen.held;
            if (held.phase() == Phase.HIDDEN && seen.terminal != null && seen.terminal.actorId() == actorId
                    && seen.terminal.key().epoch().equals(held.key().epoch())) held = seen.terminal;
            var commit = seen.committed.get(held.key().epoch());
            if (held.phase().drawsHeld() && commit != null && commit.key().equals(held.key())) {
                return new VisualPhase(Phase.RELEASED, commit.releaseTick());
            }
            return new VisualPhase(held.phase(), held.phaseTick());
        }
    }
}
