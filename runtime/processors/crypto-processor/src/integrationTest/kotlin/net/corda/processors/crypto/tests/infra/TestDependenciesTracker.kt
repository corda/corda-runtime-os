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
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.utilities.concurrent.getOrThrow
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture

class TestDependenciesTracker(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val lifecycleRegistry: LifecycleRegistry,
    private val dependencies: Set<LifecycleCoordinatorName>
) : Lifecycle {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var registrationHandle: RegistrationHandle? = null

    private val coordinatorName = LifecycleCoordinatorName.forComponent<TestDependenciesTracker>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, ::eventHandler)

    private val allDependenciesUp = CompletableFuture<LifecycleStatus>()
    private val stopped = CompletableFuture<Unit>()

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting...")
        coordinator.start()
    }

    override fun stop() {
        throw UnsupportedOperationException(
            "TestDependenciesTracker is meant to be closed through underlying components"
        )
    }

    private fun closeResourcesAndNotify() {
        registrationHandle?.close()
        registrationHandle = null
        stopped.complete(Unit)
    }

    fun waitUntilAllUp(duration: Duration) {
        try {
            Assertions.assertTrue(allDependenciesUp.getOrThrow(duration) == LifecycleStatus.UP)
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

    // This will actually get called when any of the sub components has stopped
    fun waitUntilStopped(duration: Duration) {
        stopped.getOrThrow(duration)
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> {
                registrationHandle = coordinator.followStatusChangesByName(dependencies)
                logger.info("Registered to follow $registrationHandle")
            }
            is StopEvent -> {
                closeResourcesAndNotify()
            }
            is RegistrationStatusChangeEvent -> {
                coordinator.updateStatus(event.status)
                when (event.status) {
                    LifecycleStatus.UP -> {
                        logger.info("All required dependencies are UP...")
                        // This will only work the very first time this block gets executed
                        allDependenciesUp.complete(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        logger.info("Some or all required dependencies are DOWN...")
                        closeResourcesAndNotify()
                    }
                    else -> {
                        logger.info("Some or all required dependencies are ERROR...")
                    }
                }
            }
        }
    }
}
