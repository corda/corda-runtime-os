package net.corda.entityprocessor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription

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

    fun create(config: SmartConfig): Subscription<String, EntityRequest>

    fun createRpcSubscription(): RPCSubscription<EntityRequest, FlowEvent>

}

