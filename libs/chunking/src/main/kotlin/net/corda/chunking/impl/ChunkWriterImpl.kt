package net.corda.chunking.impl

import net.corda.chunking.ChunkWriteCallback
import net.corda.chunking.ChunkWriter
import net.corda.chunking.impl.InternalChecksum.Companion.ALGORITHM
import net.corda.chunking.toAvro
import net.corda.data.chunking.Chunk
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream

/**
 * Chunks up a binary into smaller parts and passes them to the supplied callback.
 */
class ChunkWriterImpl(val chunkSize: Int) : ChunkWriter {
    companion object {
        const val KB = 1024
        const val MB = 1024 * KB
        val log = contextLogger()
    }

    var chunkWriteCallback: ChunkWriteCallback? = null

    override fun write(identifier: SecureHash, inputStream: InputStream) {
        if (chunkWriteCallback == null) {
            throw CordaRuntimeException("Chunk write callback not set")
        }

        var chunkNumber = 0
        var actualBytesRead: Int
        val bufferByteArray = ByteArray(chunkSize)
        var offset = 0L

        // Ensure we use the same algorithm to read/write
        val messageDigest = InternalChecksum.getMessageDigest()

        // Not calling `close()` for this, just wrapping it - the owner of the inputStream
        // should call `close()` on that stream.
        val digestInputStream = DigestInputStream(inputStream, messageDigest)

        while (digestInputStream.read(bufferByteArray).also { actualBytesRead = it } != -1) {
            // Ensure we only send zero bytes in the last chunk.  Zero bytes returned from
            // the read() method is valid, though it may mean that there are just no more bytes at the moment.
            if (actualBytesRead == 0) continue

            // Only wrap the bytes we've read, and not the whole buffer.
            // We're copying the array we're using here, because otherwise it's a reference to the
            // same buffer in every chunk, which ultimately, is the content last chunk to be read.
            val trimmedByteArray = ByteArray(actualBytesRead)
            bufferByteArray.copyInto(trimmedByteArray, 0, 0, actualBytesRead)
            val byteBuffer = ByteBuffer.wrap(trimmedByteArray, 0, actualBytesRead)

            // We don't bother creating a checksum for the individual chunks.
            // We're trimmed the bytes, so [byteBuffer] implicitly contains the length of this chunk.
            writeChunk(identifier, chunkNumber, byteBuffer, offset)

            chunkNumber++
            offset += actualBytesRead
        }

        val finalChecksum = SecureHash(ALGORITHM, messageDigest.digest())

        // We always send a zero sized chunk as a marker to indicate that we've sent
        // every chunk.  It also includes the checksum of the file, and the final offset
        // is also the length of the bytes read from the stream.
        writeZeroChunk(identifier, chunkNumber, finalChecksum, offset)
    }

    private fun writeZeroChunk(
        identifier: SecureHash,
        chunkNumber: Int,
        checksum: SecureHash,
        offset: Long
    ) = chunkWriteCallback!!.onChunk(
        Chunk().also {
            it.identifier = identifier.toAvro()
            it.chunkNumber = chunkNumber
            // Must be zero size or you break the code
            it.payload = ByteBuffer.wrap(ByteArray(0))
            it.offset = offset
            it.checksum = checksum.toAvro()
        }
    )

    private fun writeChunk(
        identifier: SecureHash,
        chunkNumber: Int,
        byteBuffer: ByteBuffer,
        offset: Long
    ) = chunkWriteCallback!!.onChunk(
        Chunk().also {
            it.identifier = identifier.toAvro()
            it.chunkNumber = chunkNumber
            it.payload = byteBuffer
            it.offset = offset
        }
    )

    override fun onChunk(onChunkWriteCallback: ChunkWriteCallback) {
        chunkWriteCallback = onChunkWriteCallback
    }
}
