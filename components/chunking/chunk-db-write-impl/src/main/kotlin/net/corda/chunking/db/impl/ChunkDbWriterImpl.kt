package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.ChunkDbWriter
import net.corda.data.chunking.Chunk
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.Subscription
import net.corda.virtualnode.rpcops.common.VirtualNodeSender

class ChunkDbWriterImpl internal constructor(
    private val subscription: Subscription<RequestId, Chunk>,
    private val publisher: Publisher,
    private val virtualNodeSender: VirtualNodeSender
) : ChunkDbWriter {
    override val isRunning get() = subscription.isRunning

    override fun start() {
        subscription.start()
    }

    override fun stop() {
        subscription.stop()
    }

    override fun close() {
        publisher.close()
        subscription.close()
        virtualNodeSender.close()
    }
}
