package net.corda.crypto.component.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * A base abstract class that can be used to provide basic functionality relating to lifecycle management for components
 * which depend on the configuration. It can handle the bootstrap config as well by making sure that the active
 * implementation is activated only after receiving both configs - bootstrap and the normal. To enable bootstrap
 * handling just override the isReady to by like: `override fun isReady(): Boolean = bootConfig != null`. The bootstrap
 * configuration, if required, will be received only once.
 */
@Suppress("LongParameterList")
abstract class AbstractConfigurableComponent<IMPL : AbstractConfigurableComponent.AbstractImpl>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val myName: LifecycleCoordinatorName,
    private val configurationReadService: ConfigurationReadService,
    private val upstream: DependenciesTracker,
    private val configKeys: Set<String>
) : Lifecycle {

   interface AbstractImpl: AutoCloseable {
        val downstream: DependenciesTracker
        override fun close() {
            downstream.clear()
        }
        fun onUpstreamRegistrationStatusChange(isUpstreamUp: Boolean, isDownstreamUp: Boolean?) = Unit
        fun onDownstreamRegistrationStatusChange(isUpstreamUp: Boolean, isDownstreamUp: Boolean?) = Unit
    }

    abstract class DownstreamAlwaysUpAbstractImpl : AbstractImpl {
        override val downstream: DependenciesTracker = DependenciesTracker.AlwaysUp()
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        val configReaderName = LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
        require(upstream.dependencies.contains(configReaderName)) {
            "The upstream dependencies must contain $configReaderName"
        }
    }

    private val activationFailureCounter = AtomicInteger(0)

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var _impl: IMPL? = null

    @Volatile
    private var lastConfigChangedEvent: ConfigChangedEvent? = null

    @Volatile
    protected var bootConfig: SmartConfig? = null

    val impl: IMPL get() {
        val tmp = _impl
        if(tmp == null || lifecycleCoordinator.status != LifecycleStatus.UP) {
            throw IllegalStateException("Component $myName is not ready.")
        }
        return tmp
    }

    val lifecycleCoordinator = coordinatorFactory.createCoordinator(myName, ::eventHandler)

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        lifecycleCoordinator.stop()
    }

    @Suppress("ComplexMethod")
    protected open fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is StartEvent -> {
                upstream.follow(coordinator)
            }
            is StopEvent -> {
                onStop()
            }
            is RegistrationStatusChangeEvent -> {
                if(upstream.handle(event) == DependenciesTracker.EventHandling.HANDLED) {
                    onUpstreamRegistrationStatusChange(coordinator)
                } else if(_impl?.downstream?.handle(event) == DependenciesTracker.EventHandling.HANDLED) {
                    onDownstreamRegistrationStatusChange(coordinator)
                }
            }
            is ConfigChangedEvent -> {
                onConfigChange(event, coordinator)
            }
            is BootstrapConfigProvided -> {
                if (bootConfig != null) {
                    logger.info("New bootstrap configuration received: ${event.config}, Old configuration: $bootConfig")
                    if (bootConfig != event.config) {
                        val errorString = "An attempt was made to set the bootstrap configuration twice with " +
                                "different config. Current: $bootConfig, New: ${event.config}"
                        logger.error(errorString)
                        throw IllegalStateException(errorString)
                    }
                } else {
                    bootConfig = event.config
                    if(lastConfigChangedEvent != null) {
                        logger.info(
                            "Processing {} as the component is ready and already received the {}",
                            event::class.java.simpleName,
                            event::class.java.simpleName
                        )
                        onConfigChange(lastConfigChangedEvent!!, coordinator)
                    }
                }
            }
            is TryAgainCreateActiveImpl -> {
                onTryAgainCreateActiveImpl(event.configChangedEvent, coordinator)
            }
        }
    }

    protected open fun isReady(): Boolean = true

    private fun onStop() {
        upstream.clear()
        _impl?.downstream?.clear()
        configHandle?.close()
        configHandle = null
        _impl?.close()
        _impl = null
    }

    private fun onUpstreamRegistrationStatusChange(coordinator: LifecycleCoordinator) {
        logger.info(
            "onUpstreamRegistrationStatusChange(upstream={}, downstream={}).",
            upstream.isUp,
            _impl?.downstream?.isUp
        )
        updateLifecycleStatus(coordinator)
        configHandle?.close()
        configHandle = if (upstream.isUp) {
            logger.info("Registering for configuration updates.")
            configurationReadService.registerComponentForUpdates(coordinator, configKeys)
        } else {
            null
        }
        _impl?.onUpstreamRegistrationStatusChange(upstream.isUp, _impl?.downstream?.isUp)
    }

    private fun onDownstreamRegistrationStatusChange(coordinator: LifecycleCoordinator) {
        logger.info(
            "onDownstreamRegistrationStatusChange(upstream={}, downstream={}).",
            upstream.isUp,
            _impl?.downstream?.isUp
        )
        updateLifecycleStatus(coordinator)
        _impl?.onDownstreamRegistrationStatusChange(upstream.isUp, _impl?.downstream?.isUp)
    }

    private fun onConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        lastConfigChangedEvent = event
        if(isReady()) {
            doActivation(event, coordinator)
            updateLifecycleStatus(coordinator)
        } else {
            logger.info("The {} will not be processed as the component is not ready yet", event::class.java.simpleName)
        }
    }

    private fun onTryAgainCreateActiveImpl(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        if(_impl != null || !upstream.isUp) {
            logger.info(
                "onTryAgainCreateActiveImpl skipping as stale (upstream={}, _impl={}).",
                upstream.isUp,
                _impl
            )
            return
        }
        doActivation(event, coordinator)
        updateLifecycleStatus(coordinator)
    }

    private fun doActivation(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Activating {}", myName)
        try {
            _impl?.downstream?.clear()
            _impl?.close()
            _impl = createActiveImpl(event)
            _impl?.downstream?.follow(coordinator)
            activationFailureCounter.set(0)
            logger.debug("Activated {}", myName)
        } catch (e: FatalActivationException) {
            logger.error("Failed activate", e)
            coordinator.updateStatus(LifecycleStatus.ERROR)
        } catch (e: Throwable) {
            if(activationFailureCounter.incrementAndGet() <= 5) {
                logger.warn("Failed activate..., will try again", e)
                coordinator.postEvent(TryAgainCreateActiveImpl(event))
            } else {
                logger.error("Failed activate, giving up", e)
                coordinator.updateStatus(LifecycleStatus.ERROR)
            }
        }
    }

    private fun updateLifecycleStatus(coordinator: LifecycleCoordinator) {
        logger.debug(
            "updateStatus(self={},upstream={}, downstream={}, _impl={}).",
            coordinator.status,
            upstream.isUp,
            _impl?.downstream?.isUp,
            _impl
        )
        if (upstream.isUp && _impl?.downstream?.isUp == true && _impl != null) {
            logger.info("Setting the status of {} UP", myName)
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            if(coordinator.status != LifecycleStatus.ERROR) {
                logger.info("Setting the status of {} DOWN", myName)
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    /**
     * Override that method to create the active implementation.
     */
    protected abstract fun createActiveImpl(event: ConfigChangedEvent): IMPL

    data class BootstrapConfigProvided(val config: SmartConfig) : LifecycleEvent

    data class TryAgainCreateActiveImpl(val configChangedEvent: ConfigChangedEvent) : LifecycleEvent
}