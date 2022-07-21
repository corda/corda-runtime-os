package net.corda.crypto.component.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
        override fun close() = Unit
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

    private val downstream: DependenciesTracker? get() = _impl?.downstream

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
                } else if(downstream?.handle(event) == DependenciesTracker.EventHandling.HANDLED) {
                    onDownstreamRegistrationStatusChange(coordinator)
                }
            }
            is ConfigChangedEvent -> {
                onConfigChange(event, coordinator)
            }
            is TryAgainCreateActiveImpl -> {
                onTryAgainCreateActiveImpl(event.configChangedEvent, coordinator)
            }
        }
    }

    private fun onStop() {
        upstream.clear()
        downstream?.clear()
        configHandle?.close()
        configHandle = null
        _impl?.close()
        _impl = null
    }

    private fun onUpstreamRegistrationStatusChange(coordinator: LifecycleCoordinator) {
        logger.info("onUpstreamRegistrationStatusChange(upstream={}, downstream={}).", upstream.isUp, downstream?.isUp)
        updateStatus(coordinator)
        configHandle?.close()
        configHandle = if (upstream.isUp) {
            logger.info("Registering for configuration updates.")
            configurationReadService.registerComponentForUpdates(coordinator, configKeys)
        } else {
            null
        }
        _impl?.onUpstreamRegistrationStatusChange(upstream.isUp, downstream?.isUp)
    }

    private fun onDownstreamRegistrationStatusChange(coordinator: LifecycleCoordinator) {
        logger.info("onDownstreamRegistrationStatusChange(upstream={}, downstream={}).", upstream.isUp, downstream?.isUp)
        updateStatus(coordinator)
        _impl?.onDownstreamRegistrationStatusChange(upstream.isUp, downstream?.isUp)
    }

    private fun onConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        doActivation(event, coordinator)
        updateStatus(coordinator)
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
        updateStatus(coordinator)
    }

    private fun doActivation(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Activating {}", myName)
        try {
            downstream?.clear()
            _impl?.close()
            _impl = createActiveImpl(event)
            downstream?.follow(coordinator)
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

    private fun updateStatus(coordinator: LifecycleCoordinator) {
        if (upstream.isUp && downstream?.isUp == true && _impl != null) {
            setUp(coordinator)
        } else {
            if(coordinator.status != LifecycleStatus.ERROR) {
                setDown(coordinator)
            }
        }
    }

    private fun setUp(coordinator: LifecycleCoordinator) {
        if(coordinator.status != LifecycleStatus.UP) {
            logger.info("Setting the status of {} UP", myName)
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private fun setDown(coordinator: LifecycleCoordinator) {
        if(coordinator.status != LifecycleStatus.DOWN) {
            logger.info("Setting the status of {} DOWN", myName)
            coordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    protected abstract fun createActiveImpl(event: ConfigChangedEvent): IMPL

    class TryAgainCreateActiveImpl(val configChangedEvent: ConfigChangedEvent) : LifecycleEvent
}