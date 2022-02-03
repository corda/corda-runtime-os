package net.corda.cpi.upload.endpoints.common

import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.messaging.api.publisher.RPCSender
import org.osgi.service.component.annotations.Component
import java.time.Duration

@Component(service = [CpiUploadManager::class])
class CpiUploadManagerImpl : CpiUploadManager {

    private var rpcSender: RPCSender<Chunk, ChunkAck>? = null

    private var timeout: Duration? = null

    override fun setRpcSender(rpcSender: RPCSender<Chunk, ChunkAck>) {
        this.rpcSender = rpcSender
    }

    override fun setRpcRequestTimeout(timeoutMillis: Int) {
        timeout = Duration.ofMillis(timeoutMillis.toLong())
    }

    override val isRunning: Boolean
        get() = rpcSender != null && rpcSender!!.isRunning && timeout != null

    override fun sendCpiChunk(chunk: Chunk): ChunkAck {
        TODO("Not yet implemented")
    }

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        rpcSender?.close()
        rpcSender = null
    }
}