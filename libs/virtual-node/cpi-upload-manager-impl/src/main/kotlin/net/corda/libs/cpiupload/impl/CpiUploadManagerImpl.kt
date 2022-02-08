package net.corda.libs.cpiupload.impl

import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.toCorda
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CPIUploadResponse
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*

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

    override fun uploadCpi(inputStream: InputStream): CPIUploadResponse {
        val fileName = randomFileName() // Maybe move to sendCpiChunk parameters?
        val chunkWriter = ChunkWriterFactory.create(TODO_CHUNK_SIZE)
        var lastChunk: Chunk? = null
        chunkWriter.onChunk { chunk ->
            rpcSender.sendRequest(chunk).also {
                // TODO - Wait on the future once db worker part is implemented.
                //val chunkAck = it.getOrThrow(timeout)
                val chunkAck = createDummyChunkAck(chunk)
                val chunkAckUniqueId = "{${chunkAck.requestId}, ${chunkAck.partNumber}}"
                if (chunkAck.success) {
                    log.debug("Successful ACK for chunk: $chunkAckUniqueId")
                } else {
                    // If we received unsuccessful ACK for a chunk we stop sending more chunks.
                    // The db worker should stop waiting chunks for this CPI and throw away received chunks(?).
                    var errMsg = "Unsuccessful ACK for chunk: $chunkAckUniqueId."
                    chunkAck.exception?.let {
                        errMsg += " Exception was ${it.errorType}: ${it.errorMessage}"
                    }
                    log.warn(errMsg)
                    throw CpiUploadManagerException(errMsg)
                }
            }

            if (chunk.data.limit() == 0) {
                lastChunk = chunk
            }
        }
        chunkWriter.write(fileName, inputStream)

        return CPIUploadResponse(lastChunk!!.requestId, lastChunk!!.checksum.toCorda())
    }

    override val isRunning get() = _isRunning
    private var _isRunning = false

    override fun start() {
        _isRunning = true
    }

    override fun stop() {
        _isRunning = false
    }

    // TODO Create a dummy response and return it for now.
    @VisibleForTesting
    internal var createDummyChunkAck: (Chunk) -> ChunkAck =
        {
            ChunkAck(
                it.requestId,
                it.partNumber,
                true,
                null
            )
        }
}