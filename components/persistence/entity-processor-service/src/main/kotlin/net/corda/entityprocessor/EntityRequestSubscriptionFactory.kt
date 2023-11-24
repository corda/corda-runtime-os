package net.corda.entityprocessor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.messaging.api.subscription.RPCSubscription

/**
 * The [EntityRequestSubscriptionFactory] creates a new subscription to the durable topic used to receive
 * [EntityRequest] messages.
 */
interface EntityRequestSubscriptionFactory {
    /**
     * Create a new subscription
     *
     * @param config Configuration for the subscription
     * @return A new subscription for [EntityRequest] messages
     */

    fun createRpcSubscription(): RPCSubscription<EntityRequest, FlowEvent>

}

