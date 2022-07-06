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
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
abstract class AbstractConfigurableComponent<IMPL : AbstractConfigurableComponent.AbstractImpl>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val myName: LifecycleCoordinatorName,
    private val configurationReadService: ConfigurationReadService,
    private val upstream: DependenciesTracker,
    private val configKeys: Set<String> = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.BOOT_CONFIG)
) : Lifecycle {

    interface AbstractImpl: AutoCloseable {
        val downstream: DependenciesTracker
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Volatile
    private var configHandle: AutoCloseable? = null

    @Volatile
    private var _impl: IMPL? = null

    private val downstream: DependenciesTracker? get() = _impl?.downstream

    val impl: IMPL get() = _impl ?: throw IllegalStateException("Component $myName is not ready.")

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
                if(upstream.on(event) == DependenciesTracker.EventHandling.HANDLED) {
                    onUpstreamRegistrationStatusChange(coordinator)
                } else if(downstream?.on(event) == DependenciesTracker.EventHandling.HANDLED) {
                    onDownstreamRegistrationStatusChange()
                }
            }
            is ConfigChangedEvent -> {
                onConfigChange(event, coordinator)
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
        if (upstream.isUp && downstream?.isUp == true) {
            setUp()
        } else {
            setDown()
            if (upstream.isUp) {
                logger.info("Registering for configuration updates.")
                configHandle = configurationReadService.registerComponentForUpdates(coordinator, configKeys)
            }
        }
    }

    private fun onDownstreamRegistrationStatusChange() {
        if (upstream.isUp && downstream?.isUp == true) {
            setUp()
        } else {
            setDown()
        }
    }

    private fun onConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Activating {}", myName)
        try {
            downstream?.clear()
            _impl?.close()
            _impl = createActiveImpl(event)
            downstream?.follow(coordinator)
        } catch (e: Throwable) {
            logger.error("Failed activate...", e)
            throw e
        }
        logger.debug("Activated {}", myName)
    }

    private fun setUp() {
        configHandle?.close()
        configHandle = null
        if(lifecycleCoordinator.status != LifecycleStatus.UP) {
            logger.info("Setting the status of {} UP", myName)
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private fun setDown() {
        if(lifecycleCoordinator.status != LifecycleStatus.DOWN) {
            logger.info("Setting the status of {} DOWN", myName)
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    protected abstract fun createActiveImpl(event: ConfigChangedEvent): IMPL
}