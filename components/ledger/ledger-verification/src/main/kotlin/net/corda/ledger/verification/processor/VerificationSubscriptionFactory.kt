package net.corda.ledger.verification.processor

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.Subscription

/**
 * The [VerificationSubscriptionFactory] creates a new subscription to the durable topic used to receive
 * [VerifyContractsRequest] messages.
 */
interface VerificationSubscriptionFactory {
    /**
     * Create a new subscription
     *
     * @param config Configuration for the subscription
     * @return A new subscription for [VerifyContractsRequest] messages
     */
    fun create(config: SmartConfig): Subscription<String, VerifyContractsRequest>
}