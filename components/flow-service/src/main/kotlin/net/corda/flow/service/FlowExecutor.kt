package net.corda.flow.service

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

@Suppress("LongParameterList")
class FlowExecutor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val config: SmartConfig,
    private val subscriptionFactory: SubscriptionFactory,
    private val flowEventProcessorFactory: FlowEventProcessorFactory
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
        private const val GROUP = "FlowEventConsumer"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }

    private var messagingSubscription: StateAndEventSubscription<FlowKey, Checkpoint, FlowEvent>? = null

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting the flow executor" }
                val instanceId = config.getInt(INSTANCE_ID)
                messagingSubscription = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(GROUP, FLOW_EVENT_TOPIC, instanceId),
                    flowEventProcessorFactory.create(),
                    config
                )
                messagingSubscription?.start()
            }
            is StopEvent -> {
                logger.debug { "Flow executor terminating" }
                messagingSubscription?.close()
            }
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.close()
    }
}
