package net.corda.components.examples.stateevent

import com.typesafe.config.Config
import net.corda.components.examples.stateevent.processor.DemoStateAndEventProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunStateEventSub(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    private val instanceId: Int,
    private var config: Config,
    private val subscriptionFactory: SubscriptionFactory,
    private val killProcessOnRecord: Int = 0,
    private val delayOnNext: Long = 0
) : LifeCycle {

    private companion object {
        val log: Logger = contextLogger()
        const val groupName = "stateEventGroup"
        const val eventTopic = "eventTopic"
        const val stateTopic = "stateTopic"
    }

    private var subscription: StateAndEventSubscription<String, DemoRecord, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: Config) {
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
                StateAndEventSubscriptionConfig(groupName, instanceId, stateTopic, eventTopic),
                processor,
                mapOf()
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