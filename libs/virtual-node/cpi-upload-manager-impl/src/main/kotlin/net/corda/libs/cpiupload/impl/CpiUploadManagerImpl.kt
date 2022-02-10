package net.corda.libs.cpiupload.impl

import net.corda.chunking.ChunkWriterFactory
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerException
import net.corda.libs.cpiupload.RequestId
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Future

class CpiUploadManagerImpl(
    @Suppress("UNUSED_PARAMETER")
    config: SmartConfig,
    private val rpcSender: RPCSender<Chunk, ChunkAck>,
) : CpiUploadManager {

    companion object {
        private fun randomFileName(): Path = Paths.get("/tmp/${UUID.randomUUID()}")

        private const val TODO_CHUNK_SIZE = 1024 // TODO Replace with config.

        val log = contextLogger()
    }

    private val timeout = Duration.ofMillis(1000) // TODO Replace with config.

    @VisibleForTesting
    internal data class ChunkId(val requestId: String, val partNumber: Int)

    override fun uploadCpi(inputStream: InputStream): RequestId {
        val fileName = randomFileName() // Maybe move to sendCpiChunk parameters?
        val chunkWriter = ChunkWriterFactory.create(TODO_CHUNK_SIZE)
        val sentChunkIds = mutableSetOf<ChunkId>()
        val chunkAcksFutures = mutableListOf<Future<ChunkAck>>()
        chunkWriter.onChunk { chunk ->
            if (!rpcSender.isRunning) {
                // TODO - need to notify DB worker for the abort so that he stops expecting chunks for this CPI.
                throw CpiUploadManagerException("RPCSender has stopped running. Aborting CPI uploading.")
            }
            val chunkAckFuture = rpcSender.sendRequest(chunk)
            chunkAcksFutures.add(chunkAckFuture)
            // Keep in memory only chunk's unique id so don't keep in memory its binary chunk.
            sentChunkIds.add(ChunkId(chunk.requestId, chunk.partNumber))
        }
        chunkWriter.write(fileName, inputStream)

        checkChunksSuccessfullyReceived(chunkAcksFutures, sentChunkIds)
        return sentChunkIds.first().requestId
    }

    private fun checkChunksSuccessfullyReceived(
        chunkAcksFutures: List<Future<ChunkAck>>,
        sentChunkIds: Set<ChunkId>
    ) {
        // Check that all the chunks are received by the db worker and that their acks are successful.
        chunkAcksFutures.forEach {
            val chunkAck = it.getOrThrow(timeout)
            val chunkId = ChunkId(chunkAck.requestId, chunkAck.partNumber)

            if (chunkId !in sentChunkIds) {
                log.warn("Received unexpected chunk with id: $chunkId")
                return@forEach
            }
            if (chunkAck.success) {
                log.debug("Successful ACK for chunk: $chunkId")
            } else {
                var errMsg = "Unsuccessful ACK for chunk: $chunkId."
                chunkAck.exception?.let { exceptionEnvelope ->
                    errMsg += " Error was ${exceptionEnvelope.errorType}: ${exceptionEnvelope.errorMessage}"
                }
                log.warn(errMsg)
                throw CpiUploadManagerException(errMsg)
            }
        }
    }

    override val isRunning get() = _isRunning
    private var _isRunning = false

    override fun start() {
        _isRunning = true
    }

    override fun stop() {
        _isRunning = false
    }
}