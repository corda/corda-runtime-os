package net.corda.entityprocessor.impl

import net.corda.data.virtualnode.EntityRequest
import net.corda.entityprocessor.EntityProcessor
import net.corda.messaging.api.subscription.Subscription

/**
 * Entity processor.  Starts the subscription, which in turn passes the messages to the [EntityMessageProcessor].
 */
class EntityProcessorImpl(private val subscription: Subscription<String, EntityRequest>) : EntityProcessor {
    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun start() = subscription.start()

    override fun stop() = subscription.stop()
}
