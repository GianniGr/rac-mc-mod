package com.github.giannigr.racmc.asset.rescue;

import com.github.giannigr.racmc.asset.AssetFormatException;
import com.github.giannigr.racmc.asset.MobyClassReader;
import com.github.giannigr.racmc.asset.Rac1AnimatedAsset;
import com.github.giannigr.racmc.asset.Rac1Animation;
import com.github.giannigr.racmc.asset.Rac1AnimationEvaluator;
import com.github.giannigr.racmc.asset.Rac1AssetExtractor;
import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1DiscTables;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.disc.Rac1LevelHeader;
import com.github.giannigr.racmc.iso.Rac1DiscRevision;
import com.github.giannigr.racmc.iso.Rac1MediaLease;
import com.github.giannigr.racmc.wad.WadDecoder;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.io.IOException;

/** Immutable-in-use, in-memory presentation; writes no sidecar, models, or disc data. */
public record Rac1PodAssets(String discKey, PosedModel hull, PosedModel occupant) {
    public record PosedModel(Rac1AnimatedAsset model, float[] positions, float[] normals) {}

    /** The worker borrows a fully validated local image; no path-open or hash fallback. */
    public static Rac1PodAssets read(Rac1MediaLease image) throws IOException, AssetFormatException,
            DiscFormatException, WadFormatException {
        var revision = image.revision();
        long headerSector = Rac1DiscTables.read(image).levelSector(0);
        long expectedSector = revision == Rac1DiscRevision.PAL_V2_00 ? 1886014 : 1885903;
        if (headerSector != expectedSector) throw new AssetFormatException("Wrong rescue art level header");
        byte[] header = image.readSectors(headerSector, 5);
        if (Rac1LevelHeader.of(header).id() != 0) throw new AssetFormatException("Expected Veldin L00");
        var span = Rac1RescueScenePose.firstChunk(header, revision, image.imageSizeBytes());
        byte[] compressed = image.readSectors(span.first(), span.count());
        if (WadDecoder.compressedSize(compressed) > Rac1RescueScenePose.MAX_WAD_BYTES) {
            throw new AssetFormatException("S01 WAD exceeds supported bounds");
        }
        var level = Rac1LevelAssets.load(image, 0);
        var hull = Rac1AssetExtractor.readAnimatedAsset(level, 1515);
        var folded = Rac1AssetExtractor.readAnimatedAsset(level, Rac1RescueScenePose.CLASS);
        checkModel(hull, 5, 2172, 2004, 4);
        checkModel(folded, Rac1RescueScenePose.JOINTS, 2432, 2500, 2);
        var entry = level.coreIndex().mobyClass(folded.classNumber());
        float classScale = MobyClassReader.of(level.classBlob(entry.offset())).scale();
        var scenePose = Rac1RescueScenePose.frameZero(WadDecoder.decode(compressed), revision, classScale);
        return new Rac1PodAssets(image.discKey(), pose(hull, hull.animations()[0]), pose(folded, scenePose));
    }

    private static void checkModel(Rac1AnimatedAsset model, int joints, int vertices, int triangles, int materials)
            throws AssetFormatException {
        if (model.skeleton().jointCount() != joints || model.mesh().vertexCount() != vertices
                || model.mesh().triangleCount() != triangles || model.textures().length != materials
                || model.animations().length != 1 || model.animations()[0].keyCount() != 1) {
            throw new AssetFormatException("Unexpected rescue model or rig: " + model.classNumber());
        }
    }

    private static PosedModel pose(Rac1AnimatedAsset model, Rac1Animation frame) {
        var mesh = model.mesh();
        float[] positions = new float[mesh.vertexCount() * 3];
        float[] normals = new float[positions.length];
        var matrices = Rac1AnimationEvaluator.evaluate(model.skeleton(), frame, 0, Rac1AssetExtractor.UNIT_SCALE);
        Rac1AnimationEvaluator.pose(mesh, matrices, positions, normals);
        return new PosedModel(model, positions, normals);
    }
}
