package net.corda.crypto.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
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
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractConfigurableComponent<IMPL: AutoCloseable>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    myName: LifecycleCoordinatorName,
    private val configurationReadService: ConfigurationReadService,
    @Volatile
    internal var impl: IMPL,
    private val dependencies: Set<LifecycleCoordinatorName>
) : Lifecycle {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    internal val lifecycleCoordinator = coordinatorFactory.createCoordinator(myName, ::eventHandler)

    @Volatile
    private var registrationHandle: RegistrationHandle? = null

    @Volatile
    private var configHandle: AutoCloseable? = null

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
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(dependencies)
            }
            is StopEvent -> {
                registrationHandle?.close()
                registrationHandle = null
                configHandle?.close()
                configHandle = null
                deactivate("Stopping component.")
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    logger.info("Registering for configuration updates.")
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.BOOT_CONFIG)
                    )
                } else {
                    configHandle?.close()
                    configHandle = null
                    deactivate("At least one dependency is DOWN.")
                }
            }
            is ConfigChangedEvent -> {
                activate(event)
            }
        }
    }

    private fun activate(event: ConfigChangedEvent) {
        logger.info("Activating")
        swapImpl(createActiveImpl(event))
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivate(reason: String) {
        logger.info("Deactivating due {}", reason)
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN, reason)
        swapImpl(createInactiveImpl())
    }

    private fun swapImpl(newImpl: IMPL) {
        val current = impl
        impl = newImpl
        current.close()
    }

    protected abstract fun createActiveImpl(event: ConfigChangedEvent): IMPL

    protected abstract fun createInactiveImpl(): IMPL
}