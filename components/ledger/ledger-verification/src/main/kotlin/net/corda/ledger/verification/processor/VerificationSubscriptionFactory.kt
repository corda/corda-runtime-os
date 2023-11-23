package net.corda.ledger.verification.processor

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.messaging.api.subscription.RPCSubscription

/**
 * The [VerificationSubscriptionFactory] creates a new RPC subscription to handle [TransactionVerificationRequest]s.
 */
interface VerificationSubscriptionFactory {

    fun createSubscription(): RPCSubscription<TransactionVerificationRequest, FlowEvent>
}