package net.corda.components.flow.service

import com.typesafe.config.Config
import net.corda.components.sandbox.service.SandboxService
import net.corda.configuration.read.ConfigKeys.Companion.BOOTSTRAP_KEY
import net.corda.configuration.read.ConfigKeys.Companion.FLOW_KEY
import net.corda.configuration.read.ConfigKeys.Companion.MESSAGING_KEY
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.FlowManager
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
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

@Suppress("LongParameterList")
class FlowExecutor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val config: SmartConfig,
    private val configs: Map<String, SmartConfig>,
    private val subscriptionFactory: SubscriptionFactory,
    private val flowManager: FlowManager,
    private val sandboxService: SandboxService,
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
        private const val GROUP_NAME_KEY = "consumer.group"
        private const val TOPIC_KEY = "consumer.topic"
        private const val INSTANCE_ID_KEY = "instance-id"
    }

    private val bootstrapConfig: SmartConfig = configs[BOOTSTRAP_KEY] ?: throw CordaRuntimeException("Bootstrap config can not be null")
    private val flowConfig: SmartConfig = configs[FLOW_KEY] ?: throw CordaRuntimeException("Flow config can not be null")
    private val messagingConfig: SmartConfig = configs[MESSAGING_KEY] ?: throw CordaRuntimeException("Messaging config can not be null")

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }

    private var messagingSubscription: StateAndEventSubscription<FlowKey, Checkpoint, FlowEvent>? = null

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting the flow executor" }
                val topic = flowConfig.getString(TOPIC_KEY)
                val groupName = flowConfig.getString(GROUP_NAME_KEY)
                val instanceId = bootstrapConfig.getInt(INSTANCE_ID_KEY)
                val processor = FlowMessageProcessor(flowManager, sandboxService, topic)
                messagingSubscription = subscriptionFactory.createStateAndEventSubscription(
                    SubscriptionConfig(groupName, topic, instanceId),
                    processor,
                    messagingConfig
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
