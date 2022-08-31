package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.lifecycle.Resource
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.RPCSubscription

/**
 * Upon [start], listens for virtual node creation requests using an
 * `RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse>`. Persists the created virtual node to the
 * cluster database and publishes it to Kafka.
 *
 * Upon [stop], stops listening.
 *
 * Upon [close], this stops listening and closes the underlying subscription and publisher. Note that at this point the
 * writer can no longer be used and must be recreated.
 */
internal class VirtualNodeWriter internal constructor(
    private val subscription: RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>,
    private val publisher: Publisher
) : Resource {

    fun start() {
        subscription.start()
        publisher.start()
    }

    override fun close() {
        subscription.close()
        publisher.close()
    }
}
