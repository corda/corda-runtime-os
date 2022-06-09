package net.corda.flow.service

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.scheduler.FlowWakeUpScheduler
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
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
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [FlowService::class])
class FlowService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = FlowExecutor::class)
    private val flowExecutor: FlowExecutor,
    @Reference(service = FlowWakeUpScheduler::class)
    private val flowWakeUpScheduler: FlowWakeUpScheduler
) : Lifecycle {

    companion object {
        private val logger = contextLogger()

        //Hack: Removed FLOW_CONFIG from the list for now, needs to be reviewed as part of CORE-3780
        private val configSections = setOf(BOOT_CONFIG, MESSAGING_CONFIG)
    }

    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
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
                            LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>(),
                            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                            LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
                            LifecycleCoordinatorName.forComponent<FlowExecutor>(),
                        )
                    )
                flowExecutor.start()
            }

            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        configSections
                    )
                }else {
                    coordinator.updateStatus(event.status)
                }
            }

            is ConfigChangedEvent -> {
                val config = getConfig(event.config)

                /*
                 * The order is important here we need to ensure the scheduler
                 * is configured before we configure the executor to prevent a race between receiving the first
                 * state events and scheduler creating a publisher.
                 */
                flowWakeUpScheduler.onConfigChange(config)
                flowExecutor.onConfigChange(config)
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                flowExecutor.stop()
                logger.debug { "Stopping flow runner component." }
                registration?.close()
                registration = null
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

    /**
     * HACK: This hack is here until the FLOW_CONFIG section gets added.
     * see https://r3-cev.atlassian.net/browse/CORE-3780
     */
    private fun getConfig(config: Map<String, SmartConfig>): Map<String, SmartConfig> {
        val flowConfig = ConfigFactory.empty()
            .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
            .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(500000L))
            .withValue(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION, ConfigValueFactory.fromAnyRef(60000L))
            .withValue(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, ConfigValueFactory.fromAnyRef(5L))
            .withValue(FlowConfig.PROCESSING_MAX_RETRY_DELAY, ConfigValueFactory.fromAnyRef(16000L))

        val flowSmartConfig = SmartConfigFactory.create(flowConfig).create(flowConfig)
        return config + mapOf(FLOW_CONFIG to flowSmartConfig)
    }
}
