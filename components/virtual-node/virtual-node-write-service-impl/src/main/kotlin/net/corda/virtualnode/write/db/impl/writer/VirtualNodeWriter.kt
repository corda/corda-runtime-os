package net.corda.virtualnode.write.db.impl.writer

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.lifecycle.Resource
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription

/**
 * Upon [start], listens for virtual node creation requests using an
 * `RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse>`. Persists the created virtual node to the
 * cluster database and publishes it to Kafka.
 *
 * Upon [close], this stops listening and closes the underlying subscription and publisher. Note that at this point the
 * writer can no longer be used and must be recreated.
 */
internal class VirtualNodeWriter internal constructor(
    private val rpcSubscription: RPCSubscription<VirtualNodeManagementRequest, VirtualNodeManagementResponse>,
    private val asyncOperationSubscription: Subscription<String, VirtualNodeAsynchronousRequest>,
    private val publisher: Publisher
) : Resource {

    fun start() {
        rpcSubscription.start()
        publisher.start()
        asyncOperationSubscription.start()
    }

    override fun close() {
        rpcSubscription.close()
        publisher.close()
        asyncOperationSubscription.close()
    }
}
