package net.corda.flow.service

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.FlowEventExecutorFactory
import net.corda.flow.manager.FlowMetaDataFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

@Suppress("LongParameterList")
class FlowExecutor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val config: SmartConfig,
    private val subscriptionFactory: SubscriptionFactory,
    private val flowMetaDataFactory: FlowMetaDataFactory,
    private val flowEventExecutorFactory: FlowEventExecutorFactory
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
        private const val GROUP_NAME_KEY = "consumer.group"
        private const val TOPIC_KEY = "consumer.topic"
        private const val INSTANCE_ID_KEY = "instance-id"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }

    private var messagingSubscription: StateAndEventSubscription<FlowKey, Checkpoint, FlowEvent>? = null

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting the flow executor" }
                val topic = config.getString(TOPIC_KEY)
                val groupName = config.getString(GROUP_NAME_KEY)
                val instanceId = config.getInt(INSTANCE_ID_KEY)
                val processor = FlowMessageProcessor(flowMetaDataFactory, flowEventExecutorFactory)
                messagingSubscription = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(groupName, topic, instanceId),
                    processor,
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
