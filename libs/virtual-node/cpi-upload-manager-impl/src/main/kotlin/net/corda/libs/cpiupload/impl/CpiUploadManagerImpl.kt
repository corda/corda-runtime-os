package net.corda.libs.cpiupload.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.messaging.api.publisher.RPCSender
import java.time.Duration

class CpiUploadManagerImpl(
    @Suppress("UNUSED_PARAMETER")
    config: SmartConfig,
    private val rpcSender: RPCSender<Chunk, ChunkAck>,
) : CpiUploadManager {

    companion object {
        // TODO Create a dummy response and return it for now,
        //  once cpi upload db worker part is implemented, wait on future to get the real response.
        fun createDummyChunkAck(requestId: String, partNumber: Int, success: Boolean, exception: ExceptionEnvelope?) =
            ChunkAck(
                requestId,
                partNumber,
                success,
                exception
            )
    }

    private val timeout = Duration.ofMillis(1000) // TODO Replace with config.

    override fun sendCpiChunk(chunk: Chunk): ChunkAck {
        // TODO - Wait on the future once db worker part is implemented.
        val chunkAckFuture = rpcSender.sendRequest(chunk)

        return createDummyChunkAck(
            chunk.requestId,
            chunk.partNumber,
            true,
            null
        )
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