package net.corda.chunking

import java.nio.file.Path

fun interface ChunksCombined {
    /**
     * When all chunks are successfully combined into a binary blob,
     * this method is called by the reader.  The file name returned is
     * the same as the one specified in [ChunkWriter], you should use this
     * to then move the [tempPathOfBinary], e.g.
     *
     *     Files.move(tempPathOfBinary, destPath.resolve(originalFileName) /*, opts */)
     */
    fun onChunksCombined(originalFileName: Path, tempPathOfBinary: Path)
}
