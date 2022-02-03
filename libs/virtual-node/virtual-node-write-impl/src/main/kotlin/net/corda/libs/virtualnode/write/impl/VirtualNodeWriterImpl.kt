package net.corda.libs.virtualnode.write.impl

import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.libs.virtualnode.write.VirtualNodeWriter
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.RPCSubscription

/** An implementation of [VirtualNodeWriter]. */
internal class VirtualNodeWriterImpl internal constructor(
    private val subscription: RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse>,
    private val publisher: Publisher
) : VirtualNodeWriter {

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