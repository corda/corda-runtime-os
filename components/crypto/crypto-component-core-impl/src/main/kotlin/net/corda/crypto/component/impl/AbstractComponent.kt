package net.corda.crypto.component.impl

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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractComponent<IMPL: AutoCloseable>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    myName: LifecycleCoordinatorName,
    @Volatile
    var impl: IMPL,
    private val dependencies: Set<LifecycleCoordinatorName>
) : Lifecycle {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val lifecycleCoordinator = coordinatorFactory.createCoordinator(myName, ::eventHandler)

    @Volatile
    private var registrationHandle: RegistrationHandle? = null

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
                deactivateImpl("Stopping component.")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> activateImpl()
                    else -> deactivateImpl("At least one dependency is DOWN.")
                }
            }
        }
    }

    private fun activateImpl() {
        logger.info("Activating")
        swapImpl(createActiveImpl())
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivateImpl(reason: String) {
        logger.info("Deactivating due {}", reason)
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN, reason)
        swapImpl(createInactiveImpl())
    }

    private fun swapImpl(newImpl: IMPL) {
        val current = impl
        impl = newImpl
        current.close()
    }

    protected abstract fun createActiveImpl(): IMPL

    protected abstract fun createInactiveImpl(): IMPL
}