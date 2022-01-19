package net.corda.cpk.read.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.read.CpkReadService
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.packaging.CPK
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Suppress("Unused")
@Component(service = [CpkReadService::class])
class CpkReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : CpkReadService, LifecycleEventHandler, ConfigurationHandler {
    companion object {
        val log: Logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpkReadService>(this)
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    private var reader: CpkFileReader? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.debug { "Cpk Read Service starting" }
        coordinator.start()
    }

    override fun stop() {
        log.debug { "Cpk Read Service stopping" }
        coordinator.stop()
    }

    /**
     * Event loop
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is BootstrapConfigChangedEvent -> onBootstrapConfigChangedEvent(coordinator, event)
            is StopEvent -> onStopEvent()
        }
    }

    /**
     * If the thing(s) we depend on are up (only the [ConfigurationReadService]),
     * then register `this` for config updates
     */
    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configurationReadService.registerForUpdates(this)
        } else {
            configSubscription?.close()
        }
    }

    /**
     * We've received a config event that we care about, we can now write cpks
     */
    private fun onBootstrapConfigChangedEvent(coordinator: LifecycleCoordinator, event: BootstrapConfigChangedEvent) {
        val bootstrapConfig = event.config.toSafeConfig()
        if (bootstrapConfig.hasPath(CpkServiceConfigKeys.CPK_CACHE_DIR)) {
            reader?.close()
            reader = CpkFileReader.fromConfig(bootstrapConfig)
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            log.error("Need ${CpkServiceConfigKeys.CPK_CACHE_DIR} to be specified in the boot config")
        }
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent() {
        registration?.close()
        registration = null
    }

    /**
     * We depend on the [ConfigurationReadService] so we 'listen' to [RegistrationStatusChangeEvent]
     * to tell us when it is ready so we can register ourselves to handle config updates.
     */
    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configurationReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    /** received a new configuration from the configuration service (not the event loop) */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        // In this implementation we're only checking what comes in on the boot config.
        if (ConfigKeys.BOOT_CONFIG in changedKeys) {
            coordinator.postEvent(BootstrapConfigChangedEvent(config[ConfigKeys.BOOT_CONFIG]!!))
        }
    }

    override fun close() {
        configSubscription?.close()
        registration?.close()
        reader?.close()
    }

    class BootstrapConfigChangedEvent(val config: SmartConfig) : LifecycleEvent

    override fun get(cpkMetadata: CPK.Metadata): CPK? {
        if (reader == null) {
            throw CordaRuntimeException("CpkReadServiceImpl has not been initialised yet")
        }
        return reader!!.get(cpkMetadata)
    }
}
