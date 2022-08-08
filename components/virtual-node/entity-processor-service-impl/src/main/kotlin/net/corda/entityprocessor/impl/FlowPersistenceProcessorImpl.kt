package net.corda.entityprocessor.impl

import net.corda.data.persistence.EntityRequest
import net.corda.entityprocessor.FlowPersistenceProcessor
import net.corda.messaging.api.subscription.Subscription
import org.osgi.service.component.annotations.Component

/**
 * Entity processor.
 * Starts the subscription, which in turn passes the messages to the
 * [net.corda.entityprocessor.impl.internal.EntityMessageProcessor].
 */
@Component(service = [FlowPersistenceProcessor::class])
class FlowPersistenceProcessorImpl(
    private val subscription: Subscription<String, EntityRequest>
) :
    FlowPersistenceProcessor {
    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun start() = subscription.start()

    // It is important to call `subscription.close()` rather than `subscription.stop()` as the latter does not remove
    // Lifecycle coordinator from the registry, causing it to appear there in `DOWN` state. This will in turn fail
    // overall Health check's `status` check.
    override fun stop() = subscription.close()
}
