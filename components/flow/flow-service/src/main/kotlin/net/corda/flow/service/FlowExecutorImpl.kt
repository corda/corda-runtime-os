package net.corda.flow.service

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.messaging.mediator.FlowEventMediatorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.schema.configuration.BootConfig.CRYPTO_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.PERSISTENCE_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.UNIQUENESS_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.BootConfig.VERIFICATION_WORKER_REST_ENDPOINT
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(service = [FlowExecutor::class])
class FlowExecutorImpl constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val flowEventMediatorFactory: FlowEventMediatorFactory,
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig,
) : FlowExecutor {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = FlowEventMediatorFactory::class)
        flowEventMediatorFactory: FlowEventMediatorFactory,
    ) : this(
        coordinatorFactory,
        flowEventMediatorFactory,
        { cfg -> cfg.getConfig(MESSAGING_CONFIG) }
    )

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }
    private var subscriptionRegistrationHandle: RegistrationHandle? = null
    private var multiSourceEventMediator: MultiSourceEventMediator<String, Checkpoint, FlowEvent>? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            val messagingConfig = toMessagingConfig(config).withServiceEndpoints(config)
            val updatedConfigs = updateConfigsWithFlowConfig(config, messagingConfig)

            // close the lifecycle registration first to prevent down being signaled
            subscriptionRegistrationHandle?.close()
            multiSourceEventMediator?.close()

            multiSourceEventMediator = flowEventMediatorFactory.create(
                updatedConfigs,
                messagingConfig,
            )

            subscriptionRegistrationHandle = coordinator.followStatusChangesByName(
                setOf(multiSourceEventMediator!!.subscriptionName)
            )

            multiSourceEventMediator?.start()
        } catch (ex: Exception) {
            val reason = "Failed to configure the flow executor using '${config}'"
            log.error(reason, ex)
            coordinator.updateStatus(LifecycleStatus.ERROR, reason)
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

    private fun updateConfigsWithFlowConfig(
        initialConfigs: Map<String, SmartConfig>,
        messagingConfig: SmartConfig
    ): Map<String, SmartConfig> {
        val flowConfig = initialConfigs.getConfig(FLOW_CONFIG)
        val updatedFlowConfig = flowConfig
            .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(PROCESSOR_TIMEOUT)))
            .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(MAX_ALLOWED_MSG_SIZE)))

        return initialConfigs.mapValues {
            if (it.key == FLOW_CONFIG) {
                updatedFlowConfig
            } else {
                it.value
            }
        }
    }

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                log.trace { "Flow executor is stopping..." }
                subscriptionRegistrationHandle?.close()
                multiSourceEventMediator?.close()
                log.trace { "Flow executor stopped" }
            }
        }
    }

    private fun SmartConfig.withServiceEndpoints(config: Map<String, SmartConfig>) : SmartConfig {
        val bootConfig = config.getConfig(BOOT_CONFIG)

        return listOf(
            CRYPTO_WORKER_REST_ENDPOINT,
            PERSISTENCE_WORKER_REST_ENDPOINT,
            UNIQUENESS_WORKER_REST_ENDPOINT,
            VERIFICATION_WORKER_REST_ENDPOINT
        ).fold(this) { msgConfig: SmartConfig, endpoint: String ->
            msgConfig.withValue(endpoint, ConfigValueFactory.fromAnyRef(bootConfig.getString(endpoint)))
        }
    }
}
