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

    // It is important to call `subscription.close()` rather than `subscription.stop()` as the latter does not remove
    // Lifecycle coordinator from the registry, causing it to appear there in `DOWN` state. This will in turn fail
    // overall Health check's `status` check.
    override fun stop() = subscription.close()
}
