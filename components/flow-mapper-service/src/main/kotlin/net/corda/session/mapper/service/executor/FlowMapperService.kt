package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.mapper.factory.FlowMapperMetaDataFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.Executors

@Suppress("LongParameterList")
class FlowMapperService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val config: SmartConfig,
    private val flowMetaDataFactory: FlowMapperMetaDataFactory,
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()
        private const val INSTANCE_ID = "instance-id"
        private const val FLOWMAPPER_EVENT_TOPIC = "mapper.topic.flowMapperEvent"
        private const val P2P_OUT_TOPIC = "mapper.topic.p2pout"
        private const val FLOW_EVENT_TOPIC = "mapper.topic.flowEvent"
        private const val CONSUMER_GROUP = "mapper.consumer.groupName"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperService>(::eventHandler)

    private var stateAndEventSub: StateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>? = null
    private var scheduledTaskState: ScheduledTaskState? = null

    private val instanceId = config.getInt(INSTANCE_ID)
    private val flowMapperEventTopic = config.getString(FLOWMAPPER_EVENT_TOPIC)
    private val consumerGroup = config.getString(CONSUMER_GROUP)

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "FlowMapperService received event $event" }
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
                    FlowMapperMessageProcessor(
                        flowMetaDataFactory,
                        flowMapperEventExecutorFactory,
                        FlowMapperTopics(
                            P2P_OUT_TOPIC,
                            FLOWMAPPER_EVENT_TOPIC,
                            FLOW_EVENT_TOPIC
                        )
                    ),
                    config,
                    FlowMapperListener(scheduledTaskState!!, FLOWMAPPER_EVENT_TOPIC)
                )
                stateAndEventSub?.start()
            }
            is StopEvent -> {
                stateAndEventSub?.close()
                scheduledTaskState?.close()
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
