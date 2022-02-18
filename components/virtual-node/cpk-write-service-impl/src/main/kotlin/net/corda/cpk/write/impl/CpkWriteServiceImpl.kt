package net.corda.cpk.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.cpk.write.CpkWriteService
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
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.io.InputStream

@Suppress("Unused")
@Component(service = [CpkWriteService::class])
class CpkWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
) : CpkWriteService, LifecycleEventHandler {
    companion object {
        val log: Logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpkWriteService>(this)

    @VisibleForTesting
    internal var configReadServiceRegistration: RegistrationHandle? = null
    @VisibleForTesting
    internal var configSubscription: AutoCloseable? = null

    private var writer: CpkFileWriter? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.debug { "Cpk Write Service starting" }
        coordinator.start()
    }

    override fun stop() {
        log.debug { "Cpk Write Service stopping" }
        coordinator.stop()
    }

    /**
     * Event loop
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ConfigChangedEvent -> onConfigChangedEvent(event, coordinator)
            is StopEvent -> onStopEvent()
        }
    }

    /**
     * We depend on the [ConfigurationReadService] so we 'listen' to [RegistrationStatusChangeEvent]
     * to tell us when it is ready so we can register ourselves to handle config updates.
     */
    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configReadServiceRegistration?.close()
        configReadServiceRegistration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    /**
     * If the thing(s) we depend on are up (only the [ConfigurationReadService]),
     * then register `this` for config updates
     */
    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            configSubscription = configReadService.registerComponentForUpdates(
                coordinator,
                setOf(ConfigKeys.BOOT_CONFIG)
            )
        } else {
            configSubscription?.close()
        }
    }

    /**
     * We've received a config event that we care about, we can now write cpks
     */
    private fun onConfigChangedEvent(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val cfg = event.config[ConfigKeys.BOOT_CONFIG]!!
        if (cfg.hasPath(CpkServiceConfigKeys.CPK_CACHE_DIR)) {
            writer = CpkFileWriter.fromConfig(cfg)
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            log.error("Need ${CpkServiceConfigKeys.CPK_CACHE_DIR} to be specified in the boot config")
        }
    }

    /**
     * Close the registration.
     */
    private fun onStopEvent() {
        configReadServiceRegistration?.close()
        configReadServiceRegistration = null
    }

    override fun close() {
        configSubscription?.close()
        configReadServiceRegistration?.close()
    }

    override fun put(cpkMetadata: CPK.Metadata, inputStream: InputStream) {
        if (writer == null) {
            throw CordaRuntimeException("CpkWriteServiceImpl has not been initialised yet")
        }
        writer!!.put(cpkMetadata, inputStream)
    }

    override fun remove(cpkMetadata: CPK.Metadata) {
        if (writer == null) {
            throw CordaRuntimeException("CpkWriteServiceImpl has not been initialised yet")
        }
        writer!!.remove(cpkMetadata)
    }
}
