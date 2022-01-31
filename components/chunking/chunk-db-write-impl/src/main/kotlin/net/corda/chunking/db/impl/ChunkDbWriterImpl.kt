package net.corda.chunking.db.impl

import net.corda.chunking.db.ChunkDbWriter
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.RPCSubscription

class ChunkDbWriterImpl internal constructor(
    private val subscription: RPCSubscription<Chunk, ChunkAck>,
    private val publisher: Publisher
) : ChunkDbWriter {
    override val isRunning get() = subscription.isRunning

    override fun start() {
        subscription.start()
        publisher.start()
    }

    override fun stop() {
        subscription.stop()
        publisher.close()
    }
}
