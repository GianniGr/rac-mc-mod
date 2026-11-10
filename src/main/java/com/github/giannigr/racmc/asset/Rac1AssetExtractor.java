package com.github.giannigr.racmc.asset;

import com.github.giannigr.racmc.disc.DiscFormatException;
import com.github.giannigr.racmc.disc.Rac1ClassEntry;
import com.github.giannigr.racmc.disc.Rac1CoreIndex;
import com.github.giannigr.racmc.disc.Rac1LevelAssets;
import com.github.giannigr.racmc.disc.Rac1TextureEntry;
import com.github.giannigr.racmc.wad.WadFormatException;
import java.util.ArrayList;
import java.util.List;

/**
 * The converter of the assets of the game.
 *
 * <p>The converter reads the assets of a level out of a disc image and turns them into the format
 * the game of blocks uses: the geometry of the game, with its Z-up world and its units, becomes
 * the geometry of the game of blocks, with its Y-up world and its blocks.
 *
 * <p>The conversion of the axes turns the point {@code (x, y, z)} of the game into the point
 * {@code (x, z, -y)} of the game of blocks. The scale of a class is applied exactly once: to the
 * positions of the vertices of the mesh and to the places of the joints of the skeleton.
 */
public final class Rac1AssetExtractor {

    /** The number of the level of the game that holds the assets of the mod. */
    public static final int LEVEL = 1;

    /** The number of the class of the scenery that is used as the static asset. */
    public static final int STATIC_CLASS = 452;

    /** The number of the class of the character that is used as the animated asset. */
    public static final int ANIMATED_CLASS = 0;

    /**
     * The number of the class of the Blaster, the weapon of the hero.
     *
     * <p>The Blaster is one of the gadgets of the game. The list of the gadgets and the number of
     * every one of them were measured by the ReRAC project (ISC), see
     * {@code docs/plan/gadgets.md} of that project: the item 15 of the hand slot is the Blaster
     * and it has the class 168.
     */
    public static final int BLASTER_CLASS = 168;

    /**
     * The number of the units of the game in a block of the game of blocks.
     *
     * <p>The game works in a world whose unit is about a metre: the hero of the game is 1.42 units
     * tall and the pieces of scenery are a few units tall. A block of the game of blocks is a
     * metre as well, so one unit of the game is one block.
     */
    public static final float UNIT_SCALE = 1.0f;

    private Rac1AssetExtractor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reads the static asset out of the data of a level.
     *
     * @param assets the data of the level
     * @param classNumber the number of the class of the scenery
     * @return the asset
     * @throws AssetFormatException when the asset cannot be read
     */
    public static Rac1StaticAsset readStaticAsset(Rac1LevelAssets assets, int classNumber)
            throws AssetFormatException {
        Rac1CoreIndex index = assets.coreIndex();
        Rac1ClassEntry entry = index.tieClass(classNumber);
        if (entry == null || entry.offset() <= 0) {
            throw new AssetFormatException("The level has no scenery of the class " + classNumber
                    + ".");
        }
        TieClassReader reader = TieClassReader.of(classBlob(assets, entry));
        TieClassReader.Geometry geometry = reader.readGeometry();
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(UNIT_SCALE);

        int vertexCount = geometry.vertexCount();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            float[] point = transform.convert(geometry.positions()[vertex * 3],
                    geometry.positions()[vertex * 3 + 1], geometry.positions()[vertex * 3 + 2]);
            positions[vertex * 3] = point[0];
            positions[vertex * 3 + 1] = point[1];
            positions[vertex * 3 + 2] = point[2];
            uvs[vertex * 2] = geometry.uvs()[vertex * 2];
            uvs[vertex * 2 + 1] = geometry.uvs()[vertex * 2 + 1];
        }
        int[] indices = geometry.indices();
        int[] materials = geometry.materials();
        normals(positions, indices, normals);

        Rac1Texture[] textures = textures(assets, entry, index.tieTextures(),
                reader.textureCount());
        Rac1Mesh mesh = new Rac1Mesh(positions, normals, uvs, indices, materials);
        return new Rac1StaticAsset(classNumber, "tie_" + classNumber, mesh, textures);
    }

    /**
     * Reads the animated asset out of the data of a level.
     *
     * @param assets the data of the level
     * @param classNumber the number of the class of the character
     * @return the asset
     * @throws AssetFormatException when the asset cannot be read
     */
    public static Rac1AnimatedAsset readAnimatedAsset(Rac1LevelAssets assets, int classNumber)
            throws AssetFormatException {
        Rac1CoreIndex index = assets.coreIndex();
        Rac1ClassEntry entry = index.mobyClass(classNumber);
        if (entry == null || entry.offset() <= 0) {
            throw new AssetFormatException("The level has no character of the class " + classNumber
                    + ".");
        }
        MobyClassReader reader = MobyClassReader.of(classBlob(assets, entry));
        MobyClassReader.Mesh mesh = reader.readMesh();
        MobyClassReader.Skeleton skeleton = reader.readSkeleton();

        float classScale = reader.scale();
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(UNIT_SCALE);
        int vertexCount = mesh.vertexCount();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            float[] point = transform.convert(mesh.positions()[vertex * 3],
                    mesh.positions()[vertex * 3 + 1], mesh.positions()[vertex * 3 + 2]);
            positions[vertex * 3] = point[0];
            positions[vertex * 3 + 1] = point[1];
            positions[vertex * 3 + 2] = point[2];
            float[] normal = transform.convertDirection(mesh.normals()[vertex * 3],
                    mesh.normals()[vertex * 3 + 1], mesh.normals()[vertex * 3 + 2]);
            normals[vertex * 3] = normal[0];
            normals[vertex * 3 + 1] = normal[1];
            normals[vertex * 3 + 2] = normal[2];
            uvs[vertex * 2] = mesh.uvs()[vertex * 2];
            uvs[vertex * 2 + 1] = mesh.uvs()[vertex * 2 + 1];
        }

        Rac1Skeleton converted = convert(skeleton, classScale * UNIT_SCALE);
        List<Rac1Animation> animations = animations(assets, reader, classScale * UNIT_SCALE);
        Rac1Texture[] textures = textures(assets, entry, index.mobyTextures(),
                entry.textureCount());
        Rac1SkinnedMesh skinned = new Rac1SkinnedMesh(positions, normals, uvs, mesh.indices(),
                mesh.materials(), mesh.joints(), mesh.weights());
        return new Rac1AnimatedAsset(classNumber, "moby_" + classNumber, skinned, converted,
                animations.toArray(new Rac1Animation[0]), textures);
    }

    /**
     * Reads the class of a gadget of the level.
     *
     * <p>The gadgets of the game - the weapons and the tools of the hero - are not part of the
     * core data like the other classes. Every gadget is a compressed stream of its own, and the
     * table of the gadgets of the level names the stream of every gadget. The result is the same
     * class blob as the one of the other classes, so the same reader is used for it.
     *
     * @param assets the data of the level
     * @param classNumber the number of the class of the gadget
     * @return the asset
     * @throws AssetFormatException when the gadget cannot be read
     */
    public static Rac1AnimatedAsset readGadgetAsset(Rac1LevelAssets assets, int classNumber)
            throws AssetFormatException {
        Rac1CoreIndex index = assets.coreIndex();
        byte[] blob;
        try {
            blob = assets.gadgetClass(classNumber);
        } catch (DiscFormatException | WadFormatException exception) {
            throw new AssetFormatException(exception.getMessage(), exception);
        }
        MobyClassReader reader = MobyClassReader.of(blob);
        MobyClassReader.Mesh mesh = reader.readMesh();
        MobyClassReader.Skeleton skeleton = reader.readSkeleton();

        float classScale = reader.scale();
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(UNIT_SCALE);
        int vertexCount = mesh.vertexCount();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            float[] point = transform.convert(mesh.positions()[vertex * 3],
                    mesh.positions()[vertex * 3 + 1], mesh.positions()[vertex * 3 + 2]);
            positions[vertex * 3] = point[0];
            positions[vertex * 3 + 1] = point[1];
            positions[vertex * 3 + 2] = point[2];
            float[] normal = transform.convertDirection(mesh.normals()[vertex * 3],
                    mesh.normals()[vertex * 3 + 1], mesh.normals()[vertex * 3 + 2]);
            normals[vertex * 3] = normal[0];
            normals[vertex * 3 + 1] = normal[1];
            normals[vertex * 3 + 2] = normal[2];
            uvs[vertex * 2] = mesh.uvs()[vertex * 2];
            uvs[vertex * 2 + 1] = mesh.uvs()[vertex * 2 + 1];
        }

        Rac1Skeleton converted = convert(skeleton, classScale * UNIT_SCALE);
        List<Rac1Animation> animations = new ArrayList<>();
        for (int slot = 0; slot < reader.sequenceCount(); slot++) {
            int offset = reader.sequenceOffset(slot);
            if (offset <= 0) {
                continue;
            }
            MobySequenceReader.Keys keys = reader.readSequence(offset);
            animations.add(convert("sequence_" + slot, keys, reader.jointCount(),
                    classScale * UNIT_SCALE));
        }

        // The textures of a gadget are named by the entry of the class table of the level, which
        // holds the textures but no geometry for the gadgets.
        Rac1ClassEntry entry = index.mobyClass(classNumber);
        if (entry == null) {
            throw new AssetFormatException("The level has no entry for the gadget of the class "
                    + classNumber + ", so its textures are unknown.");
        }
        Rac1Texture[] textures = textures(assets, entry, index.mobyTextures(), entry.textureCount());
        Rac1SkinnedMesh skinned = new Rac1SkinnedMesh(positions, normals, uvs, mesh.indices(),
                mesh.materials(), mesh.joints(), mesh.weights());
        return new Rac1AnimatedAsset(classNumber, "gadget_" + classNumber, skinned, converted,
                animations.toArray(new Rac1Animation[0]), textures);
    }

    /**
     * Reads a crate of the game out of the data of a level.
     *
     * <p>The crates of the game are classes of the game like every other object of the game, so
     * they are read out of the table of the classes of the level. A crate is a static object: it
     * has no skeleton and no animation of its own, so only its geometry and its textures are
     * read.
     *
     * <p>The geometry of a crate of the game is one unit high and one unit wide and deep, so it
     * fits into one block of the game of blocks without being scaled. The converter does not
     * change that: the crate keeps the size and the place it has in the game.
     *
     * @param assets the data of the level
     * @param definition the description of the crate
     * @return the asset of the crate
     * @throws AssetFormatException when the crate cannot be read
     */
    public static Rac1CrateAsset readCrateAsset(Rac1LevelAssets assets,
                                                 Rac1CrateDefinition definition)
            throws AssetFormatException {
        Rac1CoreIndex index = assets.coreIndex();
        Rac1ClassEntry entry = index.mobyClass(definition.classNumber());
        if (entry == null || entry.offset() <= 0) {
            throw new AssetFormatException("The level has no crate of the class "
                    + definition.classNumber() + ".");
        }
        MobyClassReader reader = MobyClassReader.of(classBlob(assets, entry));
        MobyClassReader.Mesh mesh = reader.readMesh();
        Rac1CoordinateTransform transform = new Rac1CoordinateTransform(UNIT_SCALE);
        int vertexCount = mesh.vertexCount();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            float[] point = transform.convert(mesh.positions()[vertex * 3],
                    mesh.positions()[vertex * 3 + 1], mesh.positions()[vertex * 3 + 2]);
            positions[vertex * 3] = point[0];
            positions[vertex * 3 + 1] = point[1];
            positions[vertex * 3 + 2] = point[2];
            float[] normal = transform.convertDirection(mesh.normals()[vertex * 3],
                    mesh.normals()[vertex * 3 + 1], mesh.normals()[vertex * 3 + 2]);
            normals[vertex * 3] = normal[0];
            normals[vertex * 3 + 1] = normal[1];
            normals[vertex * 3 + 2] = normal[2];
        }
        Rac1Texture[] textures = textures(assets, entry, index.mobyTextures(),
                entry.textureCount());
        Rac1Mesh converted = new Rac1Mesh(positions, normals, mesh.uvs(), mesh.indices(),
                mesh.materials());
        return new Rac1CrateAsset(definition.key(), definition.classNumber(), converted, textures);
    }

    /**
     * Reads the chain of joints the hero holds his items on.
     *
     * <p>The first list of the joint lists of the hero is the chain of the hand. The game places
     * the items of the hand on the last joint of that chain, which is the joint 56 for the
     * OmniWrench. The chain itself is read out of the class of the hero, so the mod does not have
     * to know the number of the joint.
     *
     * @param assets the data of the level
     * @return the joints of the chain, from the root of the skeleton to the joint of the hand
     * @throws AssetFormatException when the chain cannot be read
     */
    public static int[] readHandChain(Rac1LevelAssets assets) throws AssetFormatException {
        Rac1CoreIndex index = assets.coreIndex();
        Rac1ClassEntry entry = index.mobyClass(ANIMATED_CLASS);
        if (entry == null || entry.offset() <= 0) {
            throw new AssetFormatException("The level has no entry for the class of the hero.");
        }
        byte[] blob;
        try {
            blob = assets.classBlob(entry.offset());
        } catch (DiscFormatException exception) {
            throw new AssetFormatException(exception.getMessage(), exception);
        }
        MobyClassReader reader = MobyClassReader.of(blob);
        List<MobyClassReader.JointList> lists = reader.readJointLists();
        if (lists.isEmpty() || lists.get(0).chain().length == 0) {
            throw new AssetFormatException("The class of the hero has no chain of joints for the "
                    + "items of the hand.");
        }
        byte[] chain = lists.get(0).chain();
        int[] joints = new int[chain.length];
        for (int place = 0; place < chain.length; place++) {
            joints[place] = chain[place] & 0xFF;
        }
        return joints;
    }

    /**
     * Reads the animation sequences of a class.
     *
     * <p>A class holds its sequences in its own slots. The hero of the game has no sequences of
     * his own: they live in the core data of the level and the core index holds the offsets of
     * them.
     */
    private static List<Rac1Animation> animations(Rac1LevelAssets assets, MobyClassReader reader,
                                                   float scale) throws AssetFormatException {
        List<Rac1Animation> animations = new ArrayList<>();
        boolean own = false;
        for (int slot = 0; slot < reader.sequenceCount(); slot++) {
            int offset = reader.sequenceOffset(slot);
            if (offset <= 0) {
                continue;
            }
            own = true;
            MobySequenceReader.Keys keys = reader.readSequence(offset);
            animations.add(convert("sequence_" + slot, keys, reader.jointCount(), scale));
        }
        if (own) {
            return animations;
        }
        int[] offsets = assets.coreIndex().ratchetSequences();
        byte[] coreData = assets.coreData();
        for (int slot = 0; slot < offsets.length; slot++) {
            int offset = offsets[slot];
            if (offset <= 0 || offset >= coreData.length) {
                continue;
            }
            // The sequences of the hero live in their own block of the core data, and the
            // pointers of their keys are relative to the start of that block.
            int size = assets.coreIndex().blockSize(offset);
            if (size <= 0 || offset + size > coreData.length) {
                continue;
            }
            byte[] block = new byte[size];
            System.arraycopy(coreData, offset, block, 0, size);
            MobySequenceReader.Keys keys = MobySequenceReader.read(block, 0, reader.jointCount());
            animations.add(convert("ratchet_sequence_" + slot, keys, reader.jointCount(), scale));
        }
        return animations;
    }

    /**
     * Turns the keys of a sequence into the form of the game of blocks.
     */
    private static Rac1Animation convert(String name, MobySequenceReader.Keys keys, int joints,
                                         float scale) {
        float[] rotations = new float[keys.rotations().length];
        int keyCount = joints == 0 ? 0 : keys.rotations().length / (joints * 4);
        for (int key = 0; key < keyCount; key++) {
            for (int joint = 0; joint < joints; joint++) {
                int at = (key * joints + joint) * 4;
                float[] converted = convertQuaternion(keys.rotations()[at],
                        keys.rotations()[at + 1], keys.rotations()[at + 2],
                        keys.rotations()[at + 3]);
                rotations[at] = converted[0];
                rotations[at + 1] = converted[1];
                rotations[at + 2] = converted[2];
                rotations[at + 3] = converted[3];
            }
        }
        float[] translations = new float[keys.translationValues().length];
        for (int index = 0; index < keys.translationValues().length / 3; index++) {
            float x = keys.translationValues()[index * 3] * scale;
            float y = keys.translationValues()[index * 3 + 1] * scale;
            float z = keys.translationValues()[index * 3 + 2] * scale;
            translations[index * 3] = x;
            translations[index * 3 + 1] = z;
            translations[index * 3 + 2] = -y;
        }
        return new Rac1Animation(name, keys.times(), keys.loop(), rotations,
                keys.translationKeys(), keys.translationJoints(), translations, keys.scaleKeys(),
                keys.scaleJoints(), keys.scaleValues(), keys.scaleInherited());
    }

    /**
     * Turns the skeleton of a class into the form of the game of blocks.
     */
    private static Rac1Skeleton convert(MobyClassReader.Skeleton skeleton, float scale) {
        int joints = skeleton.jointCount();
        float[] inverseBind = new float[joints * 16];
        int[] parents = skeleton.parents();
        float[] places = new float[joints * 3];
        for (int joint = 0; joint < joints; joint++) {
            // The place of the joint in the model, built out of the rest translations of its
            // chain.
            float x = skeleton.restTranslations()[joint * 3];
            float y = skeleton.restTranslations()[joint * 3 + 1];
            float z = skeleton.restTranslations()[joint * 3 + 2];
            int parent = parents[joint];
            if (parent >= 0 && parent < joint) {
                x += places[parent * 3];
                y += places[parent * 3 + 1];
                z += places[parent * 3 + 2];
            }
            places[joint * 3] = x;
            places[joint * 3 + 1] = y;
            places[joint * 3 + 2] = z;

            float[] source = new float[16];
            System.arraycopy(skeleton.inverseBind(), joint * 16, source, 0, 16);
            if (degenerate(source)) {
                // The bind pose of the joint collapsed to a point, so its inverse bind matrix
                // cannot be used. The rest pose of the joint is used instead, so that the
                // vertices that follow it keep the place the model gave them.
                for (int index = 0; index < 16; index++) {
                    source[index] = 0.0f;
                }
                source[0] = 1.0f;
                source[5] = 1.0f;
                source[10] = 1.0f;
                source[12] = -x * scale;
                source[13] = -y * scale;
                source[14] = -z * scale;
                source[15] = 1.0f;
            } else {
                source[12] *= scale;
                source[13] *= scale;
                source[14] *= scale;
            }
            float[] converted = change(source);
            System.arraycopy(converted, 0, inverseBind, joint * 16, 16);
        }
        float[] rest = new float[joints * 3];
        for (int joint = 0; joint < joints; joint++) {
            // The rest translation of a joint is the offset to its parent, not the place of the
            // joint in the model.
            float x = skeleton.restTranslations()[joint * 3] * scale;
            float y = skeleton.restTranslations()[joint * 3 + 1] * scale;
            float z = skeleton.restTranslations()[joint * 3 + 2] * scale;
            rest[joint * 3] = x;
            rest[joint * 3 + 1] = z;
            rest[joint * 3 + 2] = -y;
        }
        return new Rac1Skeleton(inverseBind, parents, rest);
    }

    /**
     * Whether the matrix of a joint is degenerate.
     *
     * <p>A matrix is degenerate when it holds a number that is not finite or when its axes are
     * much longer or much shorter than they should be.
     *
     * @param matrix the sixteen floats of the matrix
     * @return {@code true} when the matrix cannot be used
     */
    static boolean degenerate(float[] matrix) {
        for (int index = 0; index < 16; index++) {
            if (!Float.isFinite(matrix[index])) {
                return true;
            }
        }
        for (int column = 0; column < 3; column++) {
            float x = matrix[column * 4];
            float y = matrix[column * 4 + 1];
            float z = matrix[column * 4 + 2];
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length > 1.0e4f || length < 1.0e-4f) {
                return true;
            }
        }
        return false;
    }

    /**
     * The rotation of the change of the axes, as a quaternion.
     *
     * <p>The change of the axes of the game into the axes of the game of blocks is a turn of
     * ninety degrees around the first axis.
     */
    private static final float[] BASIS = { (float) (-Math.sqrt(0.5)), 0.0f, 0.0f,
            (float) Math.sqrt(0.5) };

    /**
     * Turns a quaternion of the game into the quaternion of the game of blocks.
     *
     * @param x the first component of the quaternion
     * @param y the second component of the quaternion
     * @param z the third component of the quaternion
     * @param w the fourth component of the quaternion
     * @return the four components of the quaternion of the game of blocks
     */
    public static float[] convertQuaternion(float x, float y, float z, float w) {
        float[] turned = multiply(BASIS[0], BASIS[1], BASIS[2], BASIS[3], x, y, z, w);
        // The second factor is the conjugate of the rotation of the change of the axes.
        return multiply(turned[0], turned[1], turned[2], turned[3], -BASIS[0], -BASIS[1], -BASIS[2],
                BASIS[3]);
    }

    /**
     * Turns a matrix of the game into the matrix of the game of blocks.
     *
     * @param matrix the sixteen floats of the matrix, in the order of the columns
     * @return the sixteen floats of the matrix of the game of blocks
     */
    public static float[] change(float[] matrix) {
        // The change of the axes turns the rows and the columns of the matrix around.
        int[] order = { 0, 2, 1 };
        float[] sign = { 1.0f, 1.0f, -1.0f };
        float[] result = new float[16];
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                result[column * 4 + row] = sign[row] * sign[column]
                        * matrix[order[column] * 4 + order[row]];
            }
            result[column * 4 + 3] = 0.0f;
        }
        result[12] = matrix[12];
        result[13] = matrix[14];
        result[14] = -matrix[13];
        result[15] = 1.0f;
        return result;
    }

    private static float[] multiply(float ax, float ay, float az, float aw, float bx, float by,
                                    float bz, float bw) {
        return new float[] {
                aw * bx + ax * bw + ay * bz - az * by,
                aw * by - ax * bz + ay * bw + az * bx,
                aw * bz + ax * by - ay * bx + az * bw,
                aw * bw - ax * bx - ay * by - az * bz };
    }

    /**
     * Reads the bytes of the blob of a class out of the core data of a level.
     */
    private static byte[] classBlob(Rac1LevelAssets assets, Rac1ClassEntry entry)
            throws AssetFormatException {
        try {
            return assets.classBlob(entry.offset());
        } catch (com.github.giannigr.racmc.disc.DiscFormatException exception) {
            throw new AssetFormatException(exception.getMessage(), exception);
        }
    }

    /**
     * Reads the textures of a class.
     */
    private static Rac1Texture[] textures(Rac1LevelAssets assets, Rac1ClassEntry entry,
                                          List<Rac1TextureEntry> table, int count)
            throws AssetFormatException {
        Rac1Texture[] textures = new Rac1Texture[count];
        for (int slot = 0; slot < count; slot++) {
            int index = entry.textureSlots()[slot] & 0xFF;
            if (index >= table.size()) {
                throw new AssetFormatException("A class of the game names a texture that the level "
                        + "does not have.");
            }
            Rac1TextureEntry texture = table.get(index);
            textures[slot] = Rac1TextureReader.read(assets.coreData(), assets.coreIndex()
                    .texturesBase(), assets.gsRam(), texture);
        }
        return textures;
    }

    /**
     * Computes the normals of the vertices of a mesh from its triangles.
     */
    private static void normals(float[] positions, int[] indices, float[] normals) {
        for (int triangle = 0; triangle < indices.length / 3; triangle++) {
            int a = indices[triangle * 3];
            int b = indices[triangle * 3 + 1];
            int c = indices[triangle * 3 + 2];
            float ax = positions[a * 3], ay = positions[a * 3 + 1], az = positions[a * 3 + 2];
            float bx = positions[b * 3] - ax, by = positions[b * 3 + 1] - ay,
                    bz = positions[b * 3 + 2] - az;
            float cx = positions[c * 3] - ax, cy = positions[c * 3 + 1] - ay,
                    cz = positions[c * 3 + 2] - az;
            float nx = by * cz - bz * cy;
            float ny = bz * cx - bx * cz;
            float nz = bx * cy - by * cx;
            normals[a * 3] += nx;
            normals[a * 3 + 1] += ny;
            normals[a * 3 + 2] += nz;
            normals[b * 3] += nx;
            normals[b * 3 + 1] += ny;
            normals[b * 3 + 2] += nz;
            normals[c * 3] += nx;
            normals[c * 3 + 1] += ny;
            normals[c * 3 + 2] += nz;
        }
        for (int vertex = 0; vertex < normals.length / 3; vertex++) {
            float x = normals[vertex * 3];
            float y = normals[vertex * 3 + 1];
            float z = normals[vertex * 3 + 2];
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            if (length > 0.0f) {
                normals[vertex * 3] = x / length;
                normals[vertex * 3 + 1] = y / length;
                normals[vertex * 3 + 2] = z / length;
            } else {
                normals[vertex * 3 + 1] = 1.0f;
            }
        }
    }
}
