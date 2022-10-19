package net.corda.ledger.persistence.impl

import net.corda.data.persistence.ConsensualLedgerRequest
import net.corda.ledger.persistence.ConsensualLedgerProcessor
import net.corda.messaging.api.subscription.Subscription
import org.osgi.service.component.annotations.Component

/**
 * Consensual Ledger processor.
 * Starts the subscription, which in turn passes the messages to the
 * [net.corda.ledger.persistence.impl.internal.ConsensualLedgerMessageProcessor].
 */
@Component(service = [ConsensualLedgerProcessor::class])
class ConsensualLedgerProcessorImpl(
    private val subscription: Subscription<String, ConsensualLedgerRequest>
) :
    ConsensualLedgerProcessor {
    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun start() = subscription.start()

    override fun stop() = subscription.close()
}
