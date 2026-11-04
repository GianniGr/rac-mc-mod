package com.github.giannigr.racmc.asset;

import java.util.ArrayList;
import java.util.List;

/**
 * The reader of the command lists of the graphics chip of the console.
 *
 * <p>The geometry of the classes of the game is described by a list of commands for the vector
 * unit of the console. The reader looks for the commands that unpack data into the memory of the
 * unit: the texture coordinates, the indices of the triangles and the material blocks.
 *
 * <p>The format was measured by the ReRAC project (ISC); see
 * {@code docs/formats/tfrag_rac1.md} of that project.
 */
public final class VifCommandList {

    /** The command number of an unpack. */
    private static final int UNPACK_FLAG = 0x60;

    /** The command number of a row of the mask of the destination. */
    private static final int STMASK = 0x20;

    /** The command number of a row of the destination. */
    private static final int STROW = 0x30;

    /** The command number of a column of the destination. */
    private static final int STCOL = 0x31;

    /** The command number of a multi pass. */
    private static final int MPG = 0x4A;

    /** The command number of a direct transfer. */
    private static final int DIRECT = 0x50;

    /** The command number of a direct transfer with the high bit. */
    private static final int DIRECTHL = 0x51;

    /**
     * One command of the list.
     */
    public static final class Command {

        private final int command;
        private final int count;
        private final int immediate;
        private final byte[] data;

        Command(int command, int count, int immediate, byte[] data) {
            this.command = command;
            this.count = count;
            this.immediate = immediate;
            this.data = data;
        }

        /**
         * Whether the command unpacks data into the memory of the unit.
         *
         * @return {@code true} for an unpack
         */
        public boolean isUnpack() {
            return (command & UNPACK_FLAG) == UNPACK_FLAG;
        }

        /**
         * The number of components of an element of the unpack: one to four.
         *
         * @return the number of components
         */
        public int components() {
            return (command >> 2) & 3;
        }

        /**
         * The width of an element of the unpack: zero for 32 bit, one for 16 bit, two for 8 bit and
         * three for 5 bit.
         *
         * @return the width
         */
        public int width() {
            return command & 3;
        }

        /**
         * The number of elements of the unpack.
         *
         * @return the number of elements
         */
        public int count() {
            return count == 0 ? 256 : count;
        }

        /**
         * The data of the unpack.
         *
         * @return the bytes of the data
         */
        public byte[] data() {
            return data;
        }
    }

    private final List<Command> commands = new ArrayList<>();

    private VifCommandList() {
    }

    /**
     * Reads a command list.
     *
     * @param list the bytes of the list
     * @return the commands of the list
     * @throws AssetFormatException when the list does not follow the expected layout
     */
    public static VifCommandList read(byte[] list) throws AssetFormatException {
        VifCommandList result = new VifCommandList();
        int position = 0;
        while (position + 4 <= list.length) {
            long code = readUInt32(list, position);
            int command = (int) ((code >> 24) & 0x7F);
            int count = (int) ((code >> 16) & 0xFF);
            int immediate = (int) (code & 0xFFFF);
            int payload;
            if ((command & UNPACK_FLAG) == UNPACK_FLAG) {
                int elements = count == 0 ? 256 : count;
                int size = ((32 >> (command & 3)) * (((command >> 2) & 3) + 1)) / 8;
                payload = (elements * size + 3) & ~3;
            } else {
                switch (command) {
                    case STMASK:
                        payload = 4;
                        break;
                    case STROW:
                    case STCOL:
                        payload = 16;
                        break;
                    case MPG:
                        payload = (count == 0 ? 256 : count) * 8;
                        break;
                    case DIRECT:
                    case DIRECTHL:
                        payload = (immediate == 0 ? 65536 : immediate) * 16;
                        break;
                    default:
                        payload = 0;
                        break;
                }
            }
            if (position + 4 + payload > list.length) {
                throw new AssetFormatException("A command of the graphics chip runs past the end of "
                        + "its list.");
            }
            byte[] data = new byte[payload];
            System.arraycopy(list, position + 4, data, 0, payload);
            result.commands.add(new Command(command, count, immediate, data));
            position += 4 + payload;
        }
        return result;
    }

    /**
     * The commands that unpack data into the memory of the unit.
     *
     * @return the unpack commands, in the order of the list
     */
    public List<Command> unpacks() {
        List<Command> unpacks = new ArrayList<>();
        for (Command command : commands) {
            if (command.isUnpack()) {
                unpacks.add(command);
            }
        }
        return unpacks;
    }

    private static long readUInt32(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }
}
