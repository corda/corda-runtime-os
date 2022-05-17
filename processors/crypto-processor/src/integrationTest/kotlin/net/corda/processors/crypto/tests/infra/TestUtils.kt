package net.corda.processors.crypto.tests.infra

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.time.Instant
import kotlin.random.Random
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.processors.crypto.CryptoProcessor
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue

const val RESPONSE_TOPIC = "test.response"

const val CRYPTO_CONFIGURATION_VALUE: String = "{}"

const val MESSAGING_CONFIGURATION_VALUE: String = """
            componentVersion="5.1"
            bus.busType = "INMEMORY"
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
            }
      """

const val BOOT_CONFIGURATION = """
        instanceId=1
        topicPrefix=""
        bus.busType = INMEMORY
    """

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

fun makeBootstrapConfig(config: String, extra: Map<String, SmartConfig>): SmartConfig {
    var cfg = ConfigFactory.parseString(config)
    extra.forEach {
        cfg = cfg.withValue(it.key, ConfigValueFactory.fromMap(it.value.root().unwrapped()))
    }
    return SmartConfigFactory.create(
        ConfigFactory.empty()
    ).create(
        cfg
    )
}

fun randomDataByteArray(): ByteArray {
    val random = Random(Instant.now().toEpochMilli())
    return random.nextBytes(random.nextInt(157, 311))
}


