package net.corda.processors.crypto.tests

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
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
import net.corda.processors.crypto.CryptoProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.isAccessible

inline fun <reified T> makeClientId(): String =
    "${T::class.java}-integration-test"

fun Lifecycle.stopAndWait() {
    stop()
    isStopped()
}

fun Lifecycle.startAndWait() {
    start()
    isStarted()
}

fun CryptoProcessor.startAndWait(bootConfig: SmartConfig) {
    start(bootConfig)
    eventually {
        assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
    }
}

fun Lifecycle.isStopped() = eventually {
    Assertions.assertFalse(isRunning, "Failed waiting to stop for ${this::class.java.name}")
}

fun Lifecycle.isStarted() = eventually {
    assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
}

fun makeBootstrapConfig(config: String) = SmartConfigFactory.create(
    ConfigFactory.empty()
).create(
    ConfigFactory.parseString(config)
)

fun randomDataByteArray(): ByteArray {
    val random = Random(Instant.now().toEpochMilli())
    return random.nextBytes(random.nextInt(157, 311))
}

fun <R> runTestCase(logger: Logger, testCase: KFunction<R>): R {
    logger.info("TEST CASE: ${testCase.name}")
    testCase.isAccessible = true
    return testCase.call()
}

fun <R> runTestCase(logger: Logger, testCaseArg: Any, testCase: KFunction<R>): R {
    logger.info("TEST CASE: ${testCase.name}")
    testCase.isAccessible = true
    return testCase.call(testCaseArg)
}

class TestLifecycleDependenciesTrackingCoordinator(
    coordinatorName: LifecycleCoordinatorName,
    coordinatorFactory: LifecycleCoordinatorFactory,
    vararg dependencies: Class<*>
) : Lifecycle, AutoCloseable {
    companion object {
        private val logger = contextLogger()
    }

    private val _dependencies = dependencies.map {
        LifecycleCoordinatorName(it.name)
    }.toSet()

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
            assertTrue(coordinator.status == LifecycleStatus.UP)
        }
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> {
                registrationHandle = coordinator.followStatusChangesByName(_dependencies)
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