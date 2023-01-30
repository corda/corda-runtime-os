package net.corda.processor.member

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
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory
import java.time.Duration

class TestDependenciesTracker(
    coordinatorName: LifecycleCoordinatorName,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val lifecycleRegistry: LifecycleRegistry,
    private val dependencies: Set<LifecycleCoordinatorName>
) : Lifecycle {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        registrationHandle?.close()
        coordinator.close()
    }

    fun waitUntilAllUp(duration: Duration) {
        try {
            eventually(duration = duration) {
                Assertions.assertTrue(coordinator.status == LifecycleStatus.UP)
            }
        } catch (e: Throwable) {
            val downReport = lifecycleRegistry.componentStatus().values.filter {
                it.status == LifecycleStatus.DOWN
            }.sortedBy {
                it.name.componentName
            }.joinToString(",${System.lineSeparator()}") {
                "${it.name.componentName}=${it.status}"
            }
            logger.warn(
                "LIFECYCLE COMPONENTS STILL DOWN: [${System.lineSeparator()}$downReport${System.lineSeparator()}]"
            )
            throw e
        }
        logger.info("ALL DEPENDENCIES ARE UP!!!")
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
