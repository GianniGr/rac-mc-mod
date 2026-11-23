package com.github.giannigr.racmc.sound;

import com.github.giannigr.racmc.iso.Rac1MediaLease;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Feature-local worker boundary. A path/key is a request identity, never validation authority. */
public final class Rac1ClankAudioLoad {
    public record Request(Path image,String discKey,long mediaGeneration) {
        public Request {
            image=image.toAbsolutePath().normalize();Objects.requireNonNull(discKey);
            if(mediaGeneration<0)throw new IllegalArgumentException("Invalid media generation");
        }
    }
    @FunctionalInterface public interface LeaseSource {
        Rac1MediaLease open(Request request) throws IOException;
    }
    @FunctionalInterface public interface Reader {
        Rac1ClankAudioCatalog read(Rac1MediaLease image) throws Exception;
    }
    private Rac1ClankAudioLoad() {}
    public static Rac1ClankAudioCatalog load(Request request,LeaseSource source) throws Exception {
        return load(request,source,image->Rac1ClankAudioCatalog.extract(image,image.revision()));
    }
    /** Injection is for synthetic capability tests, not a fallback validation path. */
    public static Rac1ClankAudioCatalog load(Request request,LeaseSource source,Reader reader) throws Exception {
        try(var image=source.open(request)) {
            if(image.generation()!=request.mediaGeneration()||!image.discKey().equals(request.discKey()))
                throw new IOException("Clank audio media selection changed");
            image.checkValid();
            var result=Objects.requireNonNull(reader.read(image));
            image.checkValid();
            return result;
        }
    }
}
