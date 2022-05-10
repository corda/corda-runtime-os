package net.corda.entityprocessor.impl

import net.corda.data.virtualnode.EntityRequest
import net.corda.entityprocessor.FlowPersistenceProcessor
import net.corda.messaging.api.subscription.Subscription
import org.osgi.service.component.annotations.Component

/**
 * Entity processor.  Starts the subscription, which in turn passes the messages to the [EntityMessageProcessor].
 */
@Component(service = [FlowPersistenceProcessor::class])
class FlowPersistenceProcessorImpl(
    private val subscription: Subscription<String, EntityRequest>
) :
    FlowPersistenceProcessor {
    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun start() = subscription.start()

    override fun stop() = subscription.stop()
}
