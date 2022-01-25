package net.corda.processors.crypto.tests

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.Logger
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

fun Lifecycle.isStopped() = eventually {
    Assertions.assertFalse(isRunning, "Failed waiting to stop for ${this::class.java.name}")
}

fun Lifecycle.isStarted() = eventually {
    assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
}

fun PublisherFactory.publishConfig(clientId: String, vararg value: Pair<String, String>) {
    with(createPublisher(PublisherConfig(clientId))) {
        publish(
            value.map {
                Record(
                    Schemas.Config.CONFIG_TOPIC,
                    it.second,
                    Configuration(it.first, "1")
                )
            }
        )
    }
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
    private val logger: Logger,
    coordinatorFactory: LifecycleCoordinatorFactory,
    vararg dependencies: Class<out Lifecycle>
) : AutoCloseable {

    private val registrationHandle: RegistrationHandle

    private var allApp = false

    private val coordinator = coordinatorFactory.createCoordinator<CryptoOpsTests> { event, _ ->
        logger.info("Received event $event")
        if (event is RegistrationStatusChangeEvent && event.status == LifecycleStatus.UP) {
            logger.info("All required dependencies are up...")
            allApp = true
        }
    }.also {
        it.start()
        registrationHandle = it.followStatusChangesByName(
            dependencies.map { dependency ->
                LifecycleCoordinatorName(dependency.name)
            }.toSet()
        )
        logger.info("Registered to follow $registrationHandle")
    }

    override fun close() {
        registrationHandle.close()
        coordinator.close()
    }

    fun waitUntilAllUp() {
        eventually {
            assertTrue(allApp)
        }
    }
}