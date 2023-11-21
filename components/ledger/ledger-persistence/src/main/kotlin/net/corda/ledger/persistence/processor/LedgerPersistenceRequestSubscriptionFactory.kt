package net.corda.ledger.persistence.processor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.messaging.api.subscription.RPCSubscription

/**
 * The [LedgerPersistenceRequestSubscriptionFactory] creates a new subscription to receive
 * [LedgerPersistenceRequest] messages.
 */
interface LedgerPersistenceRequestSubscriptionFactory {
    /**
     * Create a new rpc subscription
     *
     * @return A new subscription for [LedgerPersistenceRequest] messages
     */

    fun createRpcSubscription(): RPCSubscription<LedgerPersistenceRequest, FlowEvent>

}