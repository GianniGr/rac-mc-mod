package com.github.giannigr.racmc.iso;

/**
 * One file of an ISO 9660 file system.
 *
 * @param path the path of the file inside the image, for example {@code SYSTEM.CNF}
 * @param extentLba the first sector of the file inside the image
 * @param size the size of the file in bytes
 */
record Iso9660File(String path, long extentLba, long size) {

    /**
     * The byte offset of the file inside the image.
     *
     * @return the offset in bytes
     */
    long offset() {
        return extentLba * Iso9660Tree.SECTOR_SIZE;
    }
}
