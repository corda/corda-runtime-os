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
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
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

    private var unprocessedConfigChanges: MutableList<ConfigChangedEvent> = mutableListOf()

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
        logger.trace { "$myName starting..." }
        lifecycleCoordinator.start()
    }

    override fun stop() {
        logger.trace { "$myName stopping..." }
        lifecycleCoordinator.stop()
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    protected open fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.trace { "LifecycleEvent received $myName: $event" }
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
                    logger.debug { "New bootstrap configuration received: ${event.config}, Old configuration: $bootConfig" }
                    if (bootConfig != event.config) {
                        val errorString = "An attempt was made to set the bootstrap configuration twice with " +
                                "different config. Current: $bootConfig, New: ${event.config}"
                        logger.error(errorString)
                        throw IllegalStateException(errorString)
                    }
                } else {
                    bootConfig = event.config
                    if(unprocessedConfigChanges.isNotEmpty()) {
                        logger.trace { "Processing ${event::class.java.simpleName} as the component is ready " +
                                "and already received the ${event::class.java.simpleName}" }
                        unprocessedConfigChanges.forEach {
                            onConfigChange(it, coordinator)
                        }
                    }
                    unprocessedConfigChanges.clear()
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
        logger.trace { "onUpstreamRegistrationStatusChange(upstream=${upstream.isUp}, downstream=${_impl?.downstream?.isUp})." }
        updateLifecycleStatus(coordinator)
        configHandle?.close()
        configHandle = if (upstream.isUp) {
            logger.trace { "Registering for configuration updates." }
            configurationReadService.registerComponentForUpdates(coordinator, configKeys)
        } else {
            null
        }
        _impl?.onUpstreamRegistrationStatusChange(upstream.isUp, _impl?.downstream?.isUp)
    }

    private fun onDownstreamRegistrationStatusChange(coordinator: LifecycleCoordinator) {
        logger.trace { "onDownstreamRegistrationStatusChange(upstream=${upstream.isUp}, downstream=${_impl?.downstream?.isUp})." }
        updateLifecycleStatus(coordinator)
        _impl?.onDownstreamRegistrationStatusChange(upstream.isUp, _impl?.downstream?.isUp)
    }

    private fun onConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        if(isReady()) {
            doActivation(event, coordinator)
            updateLifecycleStatus(coordinator)
        } else {
            logger.trace { "The ${event::class.java.simpleName} will not be processed as the component is not ready yet" }
            unprocessedConfigChanges.add(event)
        }
    }

    private fun onTryAgainCreateActiveImpl(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        if(_impl != null || !upstream.isUp) {
            logger.debug {
                "onTryAgainCreateActiveImpl skipping as stale (upstream=${upstream.isUp}, _impl=${_impl})."
            }
            return
        }
        doActivation(event, coordinator)
        updateLifecycleStatus(coordinator)
    }

    private fun doActivation(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.trace { "Activating $myName" }
        try {
            _impl?.downstream?.clear()
            _impl?.close()
            _impl = createActiveImpl(event)
            _impl?.downstream?.follow(coordinator)
            activationFailureCounter.set(0)
            logger.trace { "Activated $myName" }
        } catch (e: FatalActivationException) {
            logger.error("$myName failed activate", e)
            coordinator.updateStatus(LifecycleStatus.ERROR)
        } catch (e: Throwable) {
            if(activationFailureCounter.incrementAndGet() <= 5) {
                logger.debug { "$myName failed activate..., will try again. Cause: ${e.message}" }
                coordinator.postEvent(TryAgainCreateActiveImpl(event))
            } else {
                logger.error("$myName failed activate, giving up", e)
                coordinator.updateStatus(LifecycleStatus.ERROR)
            }
        }
    }

    private fun updateLifecycleStatus(coordinator: LifecycleCoordinator) {
        logger.trace {
            "updateStatus(self=${coordinator.status},upstream=${upstream.isUp}, downstream=${_impl?.downstream?.isUp}, _impl=${_impl})."
        }
        if (upstream.isUp && _impl?.downstream?.isUp == true && _impl != null) {
            logger.trace { "Setting the status of $myName UP" }
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            if (coordinator.status != LifecycleStatus.ERROR && coordinator.status != LifecycleStatus.DOWN) {
                logger.trace { "Setting the status of $myName DOWN" }
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