package net.corda.flow.service

import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.manager.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.sandbox.service.SandboxService
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This component is a sketch of how the flow service might be structured using the configuration service and the flow
 * libraries to put together a component that reacts to config changes. It should be read as not a finished component,
 * but rather a suggestion of how to put together the pieces to build components.
 */
@Suppress("LongParameterList")
@Component(service = [FlowService::class])
class FlowService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = FlowEventProcessorFactory::class)
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    @Reference(service = SandboxService::class)
    private val sandboxService: SandboxService
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
    }

    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private var executor: FlowExecutor? = null

    private val coordinator = coordinatorFactory.createCoordinator<FlowService>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "FlowService received: $event" }
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting flow runner component." }
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            // HACK: This needs to change when we have the proper sandbox group service
                            // for now we need to start this version of the service as it hosts the new
                            // api we use elsewhere
                            LifecycleCoordinatorName.forComponent<SandboxService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    configHandle?.close()
                }
            }
            is NewConfigurationReceived -> {
                executor?.stop()
                val newExecutor = FlowExecutor(
                    coordinatorFactory,
                    event.config,
                    subscriptionFactory,
                    flowEventProcessorFactory
                )
                newExecutor.start()
                executor = newExecutor
            }
            is StopEvent -> {
                executor?.stop()
                logger.debug { "Stopping flow runner component." }
                registration?.close()
                registration = null
            }
        }
    }

    @Suppress("TooGenericExceptionThrown", "UNUSED_PARAMETER")
    private fun onConfigChange(keys: Set<String>, config: Map<String, SmartConfig>) {
        if (isRelevantConfigKey(keys)) {
            coordinator.postEvent(
                NewConfigurationReceived(config[BOOT_CONFIG]!!.withFallback(config[MESSAGING_CONFIG]).withFallback(config[FLOW_CONFIG]))
            )
        }
    }

    /**
     * True if any of the config [keys] are relevant to this app.
     */
    private fun isRelevantConfigKey(keys: Set<String>) : Boolean {
        return MESSAGING_CONFIG in keys || BOOT_CONFIG in keys || FLOW_CONFIG in keys
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
