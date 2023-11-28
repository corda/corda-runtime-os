package net.corda.entityprocessor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.messaging.api.subscription.RPCSubscription

/**
 * The [EntityRequestSubscriptionFactory] creates a new subscription to receive
 * [EntityRequest] messages.
 */
interface EntityRequestSubscriptionFactory {
    /**
     * Create a new rpc subscription
     *
     * @return A new subscription for [EntityRequest] messages
     */

    fun createRpcSubscription(): RPCSubscription<EntityRequest, FlowEvent>

}

