package net.corda.chunking.impl

import java.io.InputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.util.UUID
import net.corda.chunking.Checksum
import net.corda.chunking.ChunkBuilderService
import net.corda.chunking.ChunkWriteCallback
import net.corda.chunking.ChunkWriter
import net.corda.chunking.Constants.Companion.CORDA_MESSAGE_OVERHEAD
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash


/**
 * Chunks up a binary into smaller parts and passes them to the supplied callback.
 */
internal class ChunkWriterImpl(
    maxAllowedMessageSize: Int,
    private val chunkBuilderService: ChunkBuilderService,
    private val properties: Map<String, String?>? = null,
) : ChunkWriter {
    companion object {

        private fun Map<String, String?>.toAvro(): KeyValuePairList {
            return KeyValuePairList.newBuilder().setItems(
                map { KeyValuePair(it.key, it.value) }
            ).build()
        }
    }

    var chunkWriteCallback: ChunkWriteCallback? = null

    // chunk size must be smaller than the max allowed message size to allow a buffer for the rest of the message.
    val chunkSize = maxAllowedMessageSize - CORDA_MESSAGE_OVERHEAD

    override fun write(fileName: String, inputStream: InputStream): ChunkWriter.Request {
        if (chunkWriteCallback == null) {
            throw CordaRuntimeException("Chunk write callback not set")
        }

        var chunkNumber = 0
        var actualBytesRead: Int
        val bufferByteArray = ByteArray(chunkSize)
        var offset = 0L
        val identifier = UUID.randomUUID().toString()

        // Ensure we use the same algorithm to read/write
        val messageDigest = Checksum.newMessageDigest()

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
            // We've trimmed the bytes, so [byteBuffer] implicitly contains the length of this chunk.
            writeChunk(identifier, fileName, chunkNumber, byteBuffer, offset)

            chunkNumber++
            offset += actualBytesRead
        }

        val finalChecksum = SecureHash(Checksum.ALGORITHM, messageDigest.digest())

        // We always send a zero sized chunk as a marker to indicate that we've sent
        // every chunk.  It also includes the checksum of the file, and the final offset
        // is also the length of the bytes read from the stream.
        writeZeroChunk(identifier, fileName, chunkNumber, finalChecksum, offset)

        return ChunkWriter.Request(identifier, finalChecksum)
    }

    private fun writeZeroChunk(
        identifier: String,
        fileName: String,
        chunkNumber: Int,
        checksum: SecureHash,
        offset: Long
    ) = chunkWriteCallback!!.onChunk(
        chunkBuilderService.buildFinalChunk(identifier, chunkNumber, checksum, offset, properties?.toAvro(), fileName)
    )

    private fun writeChunk(
        identifier: String,
        fileName: String,
        chunkNumber: Int,
        byteBuffer: ByteBuffer,
        offset: Long
    ) = chunkWriteCallback!!.onChunk(
        chunkBuilderService.buildChunk(identifier, chunkNumber, byteBuffer, offset, properties?.toAvro(), fileName)
    )

    override fun onChunk(onChunkWriteCallback: ChunkWriteCallback) {
        if (chunkWriteCallback != null) {
            throw CordaRuntimeException("On chunk callback is already set")
        }
        chunkWriteCallback = onChunkWriteCallback
    }
}