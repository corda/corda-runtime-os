package net.corda.components.session.mapper.service

import net.corda.components.session.mapper.ScheduledTaskState
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.Executors

class FlowMapperService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val config: SmartConfig,
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()
        private const val INSTANCE_ID = "instance-id"
        private const val FLOWMAPPER_EVENT_TOPIC = "topic.mapper"
        private const val P2P_OUT_TOPIC = "topic.p2pout"
        private const val FLOW_EVENT_TOPIC = "topic.flowEventTopic"
        private const val CONSUMER_GROUP = "mapper.consumer.groupName"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperService>(::eventHandler)

    private var stateAndEventSub: StateAndEventSubscription<String, FlowMapperState, FlowEvent>? = null
    private var publisher: Publisher? = null
    private var scheduledTaskState: ScheduledTaskState? = null

    private val instanceId = config.getInt(INSTANCE_ID)
    private val flowMapperEventTopic = config.getString(FLOWMAPPER_EVENT_TOPIC)
    private val consumerGroup = config.getString(CONSUMER_GROUP)

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("DeduplicationManager received event $event")
        when (event) {
            is StartEvent -> {
                stateAndEventSub?.close()
                scheduledTaskState = ScheduledTaskState(
                    Executors.newSingleThreadScheduledExecutor(),
                    publisherFactory.createPublisher(PublisherConfig("$consumerGroup-cleanup-publisher"), config),
                    mutableMapOf()
                )
                stateAndEventSub = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(consumerGroup, flowMapperEventTopic, instanceId),
                    FlowMapperExecutor(scheduledTaskState!!, FLOWMAPPER_EVENT_TOPIC, FLOW_EVENT_TOPIC, P2P_OUT_TOPIC),
                    config,
                    FlowMapperListener(scheduledTaskState!!, FLOWMAPPER_EVENT_TOPIC)
                )
                stateAndEventSub?.start()
            }
            is StopEvent -> {
                publisher?.close()
                stateAndEventSub?.close()
                scheduledTaskState?.publisher?.close()
                scheduledTaskState?.executorService?.shutdown()
            }
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
