package net.corda.chunking.impl

import net.corda.chunking.ChunkReader
import net.corda.chunking.ChunksCombined
import net.corda.chunking.toCorda
import net.corda.data.chunking.Chunk
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Receives binary chunks and reassembles full binary and executes completed
 * callback when binary is assembled.
 */
class ChunkReaderImpl(private val destDir: Path) : ChunkReader {
    companion object {
        val log = contextLogger()
    }

    // Could replace the set with just a chunk count, but a set at least tells us which chunk(s) are
    // missing under the debugger.
    private data class ChunksReceived(
        val chunks: MutableSet<Int>,
        var expectedCount: Int,
        var expectedChecksum: SecureHash
    )

    private val chunksSoFar = mutableMapOf<String, ChunksReceived>()
    private var chunksCombinedCallback: ChunksCombined? = null

    override fun read(chunk: Chunk) {
        if (chunksCombinedCallback == null) throw CordaRuntimeException("onComplete callback not defined")

        val path = getPath(chunk.requestId)
        val chunksReceived = chunksSoFar.computeIfAbsent(chunk.requestId) {
            ChunksReceived(
                mutableSetOf(),
                0,
                SecureHash("EMPTY", ByteArray(16))
            )
        }

        // The zero sized chunk is 'special' as it marks the end of the chunks that have been sent,
        // and its number is therefore the total number of chunks that have been sent.  We don't need
        // to write it.
        if (chunk.data.limit() == 0) {
            chunksReceived.expectedCount = chunk.partNumber
            chunksReceived.expectedChecksum = chunk.checksum.toCorda()
        } else {
            // We have a chunk, move to the correct offset, and write the data.
            // We expect the data to be correct sized.  There is a unit test to
            // ensure the writer does this.
            Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE).apply {
                position(chunk.offset)
                write(chunk.data)
            }

            chunksReceived.chunks.add(chunk.partNumber)
        }

        // Have we received all the chunks?
        if (chunksReceived.expectedCount == chunksReceived.chunks.size) {
            val internalChecksum = InternalChecksum()
            val actualChecksum = internalChecksum.digestForPath(path)
            if (actualChecksum != chunksReceived.expectedChecksum) {
                throw IllegalArgumentException("Checksums do not match, one or more of the chunks may be corrupt")
            }

            chunksCombinedCallback!!.onChunksCombined(Paths.get(chunk.fileName), path)
        }
    }

    private fun getPath(fileName: String): Path = destDir.resolve(fileName)

    override fun onComplete(chunksCombinedCallback: ChunksCombined) {
        this.chunksCombinedCallback = chunksCombinedCallback
    }
}
