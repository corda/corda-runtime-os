package net.corda.ledger.consensual.persistence.impl.processor

import net.corda.data.persistence.ConsensualLedgerRequest
import net.corda.ledger.consensual.persistence.processor.ConsensualLedgerProcessor
import net.corda.messaging.api.subscription.Subscription

/**
 * Consensual Ledger processor. Starts the subscription, which in turn passes the messages to the [ConsensualLedgerMessageProcessor].
 */
class ConsensualLedgerProcessorImpl(
    private val subscription: Subscription<String, ConsensualLedgerRequest>
) : ConsensualLedgerProcessor {
    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun start() = subscription.start()

    override fun stop() = subscription.close()
}
