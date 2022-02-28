package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.ChunkDbWriter
import net.corda.data.chunking.Chunk
import net.corda.messaging.api.subscription.Subscription

class ChunkDbWriterImpl internal constructor(
    private val subscription: Subscription<RequestId, Chunk>
) : ChunkDbWriter {
    override val isRunning get() = subscription.isRunning

    override fun start() {
        subscription.start()
    }

    override fun stop() {
        subscription.stop()
    }

    override fun close() {
        subscription.close()
    }
}
