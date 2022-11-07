package net.corda.ledger.persistence.processor

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.Subscription

/**
 * The [PersistenceRequestSubscriptionFactory] creates a new subscription to the durable topic used to receive
 * [LedgerPersistenceRequest] messages.
 */
interface PersistenceRequestSubscriptionFactory {
    /**
     * Create a new subscription
     *
     * @param config Configuration for the subscription
     * @return A new subscription for [LedgerPersistenceRequest] messages
     */
    fun create(config: SmartConfig): Subscription<String, LedgerPersistenceRequest>
}