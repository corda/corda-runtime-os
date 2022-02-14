package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.RPCSubscription

/**
 * Upon [start], listens for virtual node creation requests using an
 * `RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse>`. Persists the created virtual node to the
 * cluster database and publishes it to Kafka.
 *
 * Upon [stop], stops listening.
 */
internal class VirtualNodeWriter internal constructor(
    private val subscription: RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse>,
    private val publisher: Publisher
) : Lifecycle {

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