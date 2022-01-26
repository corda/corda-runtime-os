package net.corda.chunking

import net.corda.v5.crypto.SecureHash
import java.nio.file.Path

fun interface ChunksCombined {
    /**
     * When all chunks are successfully combined into a binary blob,
     * this method is called by the reader.  The identifier returned is
     * the same as the one specified in [ChunkWriter], you should use this
     * to track the binary artifact from [ChunkWriter] to [ChunkReader]
     */
    fun onChunksCombined(identifier: SecureHash, path: Path)
}
