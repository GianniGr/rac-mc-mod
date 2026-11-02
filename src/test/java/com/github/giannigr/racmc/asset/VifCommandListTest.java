package com.github.giannigr.racmc.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focused checks for the reader of the command lists of the graphics chip of the console.
 *
 * <p>The lists that are read are built by hand. No data of the game is used anywhere.
 */
class VifCommandListTest {

    /** The number of the command that unpacks the coordinates of the texture. */
    private static final int UNPACK_COORDINATES = 0x65;

    /** The number of the command that unpacks the indices of the triangles. */
    private static final int UNPACK_INDICES = 0x6E;

    /**
     * Builds a command of a list.
     *
     * @param command the number of the command
     * @param count the number of the elements of the command
     * @param immediate the number that follows the command
     * @param payload the number of the bytes of the data of the command
     * @return the bytes of the command
     */
    private static byte[] command(int command, int count, int immediate, int payload) {
        byte[] bytes = new byte[4 + payload];
        // The word of a command of the graphics chip is read in the little endian order: the
        // number of the command is in the last byte, the number of the elements in the one
        // before it and the number that follows the command in the first two.
        bytes[0] = (byte) (immediate & 0xFF);
        bytes[1] = (byte) ((immediate >> 8) & 0xFF);
        bytes[2] = (byte) (count & 0xFF);
        bytes[3] = (byte) (command & 0x7F);
        return bytes;
    }

    @Test
    @DisplayName("The unpack commands of a list are found")
    void theUnpackCommandsAreFound() throws AssetFormatException {
        byte[] mask = command(0x20, 0, 0, 4);
        byte[] coordinates = command(UNPACK_COORDINATES, 3, 0, 12);
        byte[] row = command(0x30, 0, 0, 16);
        byte[] list = new byte[mask.length + coordinates.length + row.length];
        System.arraycopy(mask, 0, list, 0, mask.length);
        System.arraycopy(coordinates, 0, list, mask.length, coordinates.length);
        System.arraycopy(row, 0, list, mask.length + coordinates.length, row.length);

        VifCommandList commands = VifCommandList.read(list);

        List<VifCommandList.Command> unpacks = commands.unpacks();
        assertEquals(1, unpacks.size(), "The list holds one unpack");
        assertEquals(3, unpacks.get(0).count(), "The unpack holds three elements");
        assertEquals(1, unpacks.get(0).components(),
                "The unpack holds the two coordinates of the texture");
        assertEquals(1, unpacks.get(0).width(), "The unpack holds 16 bit numbers");
        assertEquals(12, unpacks.get(0).data().length);
    }

    @Test
    @DisplayName("The unpack of the indices of the triangles is found")
    void theUnpackOfTheIndicesIsFound() throws AssetFormatException {
        byte[] indices = command(UNPACK_INDICES, 2, 0, 8);

        VifCommandList commands = VifCommandList.read(indices);

        assertEquals(1, commands.unpacks().size());
        assertEquals(3, commands.unpacks().get(0).components(),
                "The unpack holds the four numbers of a vertex");
        assertEquals(2, commands.unpacks().get(0).width(), "The unpack holds 8 bit numbers");
    }

    @Test
    @DisplayName("An unpack without a number of elements holds 256 of them")
    void anUnpackWithoutACountHolds256Elements() throws AssetFormatException {
        byte[] unpack = command(UNPACK_COORDINATES, 0, 0, 1024);

        VifCommandList commands = VifCommandList.read(unpack);

        assertEquals(256, commands.unpacks().get(0).count());
    }

    @Test
    @DisplayName("A command that runs past the end of its list is refused")
    void aCommandThatRunsPastTheEndIsRefused() {
        byte[] list = command(0x30, 0, 0, 16);
        byte[] shortList = new byte[8];
        System.arraycopy(list, 0, shortList, 0, 8);

        assertThrows(AssetFormatException.class, () -> VifCommandList.read(shortList));
    }

    @Test
    @DisplayName("A list without a command has no unpack")
    void aListWithoutACommandHasNoUnpack() throws AssetFormatException {
        VifCommandList commands = VifCommandList.read(new byte[0]);

        assertTrue(commands.unpacks().isEmpty());
    }

    @Test
    @DisplayName("A direct transfer of the graphics chip is read")
    void aDirectTransferIsRead() throws AssetFormatException {
        byte[] direct = command(0x50, 0, 1, 16);

        VifCommandList commands = VifCommandList.read(direct);

        assertTrue(commands.unpacks().isEmpty(), "A direct transfer does not unpack");
    }
}
