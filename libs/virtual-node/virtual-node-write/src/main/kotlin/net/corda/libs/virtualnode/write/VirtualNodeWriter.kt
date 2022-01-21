package net.corda.libs.virtualnode.write

import net.corda.lifecycle.Lifecycle

/**
 * Upon [start], listens for virtual node creation requests using an
 * `RPCSubscription<VirtualNodeCreationRequest, VirtualNodeCreationResponse>`. Persists the created virtual node to the
 * cluster database and publishes it to Kafka.
 *
 * Upon [stop], stops listening.
 */
interface VirtualNodeWriter : Lifecycle