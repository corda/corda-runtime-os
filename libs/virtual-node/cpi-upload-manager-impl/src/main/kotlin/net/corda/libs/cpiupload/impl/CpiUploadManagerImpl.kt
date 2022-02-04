package net.corda.libs.cpiupload.impl

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

    private val timeout = Duration.ofMillis(1000) // TODO replace with config

    override fun sendCpiChunk(chunk: Chunk): ChunkAck {
        TODO("Not yet implemented")
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