package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequestRedelivery
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener

/**
 * The [RedeliveryScheduler] listens to [VerifyContractsRequestRedelivery] state changes and schedules redelivery of
 * [VerifyContractsRequest].
 */
interface RedeliveryScheduler : StateAndEventListener<String, VerifyContractsRequestRedelivery> {

    /**
     * Called when the worker configuration changes, the scheduler uses the messaging configuration section
     * when publishing the scheduled redelivery events.
     *
     * @param config map of the worker's configuration sections
     */
    fun onConfigChange(config: Map<String, SmartConfig>)
}