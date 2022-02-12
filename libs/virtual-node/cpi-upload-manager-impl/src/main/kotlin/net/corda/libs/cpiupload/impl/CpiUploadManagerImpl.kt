package net.corda.libs.cpiupload.impl

import net.corda.chunking.ChunkWriterFactory
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerException
import net.corda.libs.cpiupload.RequestId
import net.corda.messaging.api.publisher.RPCSender
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import java.io.InputStream
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Future

class CpiUploadManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<Chunk, ChunkAck>,
) : CpiUploadManager {

    companion object {
        private const val KB = 1024
        const val TODO_CHUNK_SIZE = 512 * KB // TODO Replace with config.

        // Should merge this with SmartConfigUtil.DEFAULT_ENDPOINT_TIMEOUT_MS.
        private const val DEFAULT_ENDPOINT_TIMEOUT_MS = 10000L

        val log = contextLogger()
    }

    private val requestTimeout = config.run {
        if (this.hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)) {
            Duration.ofMillis(getLong(RPC_ENDPOINT_TIMEOUT_MILLIS))
        } else {
            Duration.ofMillis(DEFAULT_ENDPOINT_TIMEOUT_MS)
        }
    }

    @VisibleForTesting
    internal data class ChunkId(val requestId: String, val partNumber: Int)

    override fun uploadCpi(cpiFileName: String, cpiContent: InputStream): RequestId {
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
        chunkWriter.write(Paths.get(cpiFileName), cpiContent)

        checkChunksSuccessfullyReceived(chunkAcksFutures, sentChunkIds)
        return sentChunkIds.first().requestId
    }

    private fun checkChunksSuccessfullyReceived(
        chunkAcksFutures: List<Future<ChunkAck>>,
        sentChunkIds: Set<ChunkId>
    ) {
        // Check that all the chunks are received by the db worker and that their acks are successful.
        chunkAcksFutures.forEach {
            val chunkAck = it.getOrThrow(requestTimeout)
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
}