package net.corda.libs.cpiupload.impl

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.seconds
import java.time.Duration

class CpiUploadManagerImpl(
    @Suppress("UNUSED_PARAMETER")
    config: SmartConfig,
    private val rpcSender: RPCSender<Chunk, ChunkAck>,
) : CpiUploadManager {

    private val timeout = Duration.ofMillis(1000) // TODO replace with config

    override fun sendCpiChunk(chunk: Chunk): ChunkAck {
        val chunkAckFuture = rpcSender.sendRequest(chunk)
        // TODO kyriakos could get a dummy response and respond
        return chunkAckFuture.getOrThrow(20.seconds)
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