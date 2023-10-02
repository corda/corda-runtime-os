package net.corda.ledger.persistence.processor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription

/**
 * The [LedgerPersistenceRequestSubscriptionFactory] creates a new subscription to the durable topic used to receive
 * [LedgerPersistenceRequest] messages.
 */
interface LedgerPersistenceRequestSubscriptionFactory {
    /**
     * Create a new subscription
     *
     * @param config Configuration for the subscription
     * @return A new subscription for [LedgerPersistenceRequest] messages
     */
    fun create(config: SmartConfig): Subscription<String, LedgerPersistenceRequest>

    fun createRpcSubscription(): RPCSubscription<LedgerPersistenceRequest, FlowEvent>

}