package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.ChunkDbWriter
import net.corda.data.chunking.Chunk
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.Subscription

class ChunkDbWriterImpl internal constructor(
    private val subscription: Subscription<RequestId, Chunk>,
    private val publisher: Publisher
) : ChunkDbWriter {
    override fun start() {
        subscription.start()
    }

    override fun close() {
        publisher.close()
        subscription.close()
    }
}
