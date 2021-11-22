package net.corda.virtualnode.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
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
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeInfoListener
import net.corda.virtualnode.VirtualNodeInfoService
import net.corda.virtualnode.component.VirtualNodeInfoProcessor
import net.corda.virtualnode.component.VirtualNodeInfoServiceComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Virtual Node Info Service Component which implements [VirtualNodeInfoService]
 *
 * You should register the callback in [VirtualNodeInfoServiceComponent]
 *
 * Note that [get()] and [getById()] methods may throw if the component is not _fully started_
 * because the configuration has not yet been received from the messaging layer.
 */
@Component(service = [VirtualNodeInfoServiceComponent::class])
class VirtualNodeInfoServiceComponentImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = VirtualNodeInfoProcessor::class)
    private val virtualNodeInfoProcessor: VirtualNodeInfoProcessor,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : VirtualNodeInfoServiceComponent, ConfigurationHandler {
    companion object {
        val log: Logger = contextLogger()
    }

    private val lock = ReentrantLock()

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoService>(::eventHandler)

    private var configSubscription: AutoCloseable? = null

    private var registration: RegistrationHandle? = null

    private var started = false

    //region Lifecycle

    /**
     * This service is only 'running' if the internal service is running.
     * That is only achieved when configuration is received.
     */
    override val isRunning: Boolean
        get() {
            return started
        }

    /**
     * Start the up the parts we need to start listening to the messaging layer.
     */
    override fun start() = lock.withLock {
        log.debug("Virtual Node Info Service component starting")
        if (!started) {
            // ensure the config service is started before we are because we need the config
            configurationReadService.start()
            coordinator.start()
            virtualNodeInfoProcessor.start()
            started = true
        } else {
            log.debug("Virtual Node Info Service component already started")
        }
    }

    override fun stop() = lock.withLock {
        log.debug("Virtual Node Info Service component stopping")
        configSubscription?.close()
        virtualNodeInfoProcessor.stop()
        coordinator.stop()
        started = false
    }

    //endregion

    //region VirtualNodeInfoService

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? = virtualNodeInfoProcessor.get(holdingIdentity)

    override fun getById(id: String): List<VirtualNodeInfo>? = virtualNodeInfoProcessor.getById(id)

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable =
        virtualNodeInfoProcessor.registerCallback(listener)

    //endregion

    //region ConfigurationHandler

    /**
     * Configuration callback.  Starts/restarts the internal [VirtualNodeInfoService].
     *
     * When we receive a callback from the [ConfigurationReadService] with configuration
     * we check to see if it contains the messaging configuration (Kafka broker addresses etc.)
     * and then actually start the internal virtual node info service.
     *
     * If there is a change, we stop, set the new config and restart.  Any callbacks registered by the user
     * are retained during an "internal" restart.  Only [close()] will remove the subscriptions.
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) =
        virtualNodeInfoProcessor.onNewConfiguration(changedKeys, config)

    //endregion

    /**
     * Event handler that deals with starting and stopping, but in particular when the
     * [ConfigurationReadService] is up, then we register our update callback.
     */
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configSubscription = configurationReadService.registerForUpdates(this)
                } else {
                    configSubscription?.close()
                }
            }
            is StopEvent -> {
                virtualNodeInfoProcessor.stop()
                registration?.close()
                registration = null
            }
        }
    }
}
