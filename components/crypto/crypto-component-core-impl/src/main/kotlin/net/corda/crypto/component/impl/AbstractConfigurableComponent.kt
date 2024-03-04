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
import net.corda.utilities.trace
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
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        val configReaderName = LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
        require(upstream.dependencies.contains(configReaderName)) {
            "The upstream dependencies must contain $configReaderName"
        }
    }

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var _impl: IMPL? = null

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
                }
            }
            is ConfigChangedEvent -> {
                doActivation(event, coordinator)
                updateLifecycleStatus(coordinator)
            }
        }
    }

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
    }

    private fun doActivation(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.trace { "Activating $myName" }
        _impl?.downstream?.clear() // doesn't throw
        _impl?.close()
        _impl = createActiveImpl(event) // doesn't throw
        _impl?.downstream?.follow(coordinator) // doesn't throw
        logger.trace { "Activated $myName" }
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
}