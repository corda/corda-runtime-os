package net.corda.components.examples.stateevent

import net.corda.components.examples.stateevent.processor.DemoStateAndEventProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class RunStateEventSub(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    private val instanceId: Int,
    private val subscriptionFactory: SubscriptionFactory,
    private val killProcessOnRecord: Int = 0,
) : LifeCycle {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val groupName = "stateEventGroup"
        const val eventTopic = "eventTopic"
        const val stateTopic = "stateTopic"
    }

    private var subscription: StateAndEventSubscription<String, DemoRecord, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    override fun start() {
        val processor = DemoStateAndEventProcessor(killProcessOnRecord)
        subscription = subscriptionFactory.createStateAndEventSubscription(
            StateAndEventSubscriptionConfig(groupName, instanceId, stateTopic, eventTopic),
            processor,
            mapOf()
        )

        subscription?.start()
    }

    override fun stop() {
        subscription?.stop()
        log.info("Stopping state and event sub")
    }
}