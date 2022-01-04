package net.corda.components.examples.stateevent

import net.corda.components.examples.stateevent.processor.DemoStateAndEventProcessor
import net.corda.data.demo.DemoRecord
import net.corda.data.demo.DemoStateRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunStateEventSub(
    private val instanceId: Int,
    private var config: SmartConfig,
    private val subscriptionFactory: SubscriptionFactory,
    private val killProcessOnRecord: Int = 0,
    private val delayOnNext: Long = 0
) : Lifecycle {

    private companion object {
        val log: Logger = contextLogger()
        const val groupName = "stateEventGroup"
        const val eventTopic = "EventTopic"
    }

    private var subscription: StateAndEventSubscription<String, DemoStateRecord, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: SmartConfig) {
        log.info("Restarting state and event subscription")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            log.info("Creating state and event subscription")
            val processor = DemoStateAndEventProcessor(killProcessOnRecord, delayOnNext)

            subscription = subscriptionFactory.createStateAndEventSubscription(
                SubscriptionConfig(groupName, eventTopic, instanceId),
                processor,
                config
            )

            log.info("Starting state and event subscription")
            subscription?.start()
        }
    }

    override fun stop() {
        log.info("Stopping state and event sub")
        subscription?.stop()
    }
}
