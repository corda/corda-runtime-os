package net.corda.processors.crypto.tests.infra

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
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Assertions
import java.time.Duration

class TestLifecycleDependenciesTrackingCoordinator(
    coordinatorName: LifecycleCoordinatorName,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val dependencies: Set<LifecycleCoordinatorName>
) : Lifecycle, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    @Volatile
    private var registrationHandle: RegistrationHandle? = null

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, ::eventHandler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping...")
        coordinator.stop()
    }

    override fun close() {
        registrationHandle?.close()
        coordinator.close()
    }

    fun waitUntilAllUp(duration: Duration) {
        eventually(duration = duration) {
            Assertions.assertTrue(coordinator.status == LifecycleStatus.UP)
        }
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> {
                registrationHandle = coordinator.followStatusChangesByName(dependencies)
                logger.info("Registered to follow $registrationHandle")
            }
            is StopEvent -> {
                registrationHandle?.close()
                registrationHandle = null
            }
            is RegistrationStatusChangeEvent -> {
                coordinator.updateStatus(event.status)
                if(event.status == LifecycleStatus.UP) {
                    logger.info("All required dependencies are UP...")
                } else {
                    logger.info("Some or all required dependencies are DOWN...")
                }
            }
        }
    }
}