package net.corda.ledger.verification.processor

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequestRedelivery
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.StateAndEventSubscription

/**
 * The [VerificationSubscriptionFactory] creates a new subscription to the durable topic used to receive
 * [VerifyContractsRequest] messages. State and Event pattern is used to support redeliveries.
 */
interface VerificationSubscriptionFactory {
    /**
     * Create a new subscription
     *
     * @param config Configuration for the subscription
     * @return A new subscription for [VerifyContractsRequest] messages
     */
    fun create(config: SmartConfig): StateAndEventSubscription<String, VerifyContractsRequestRedelivery, VerifyContractsRequest>
}