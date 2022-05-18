package net.corda.processors.crypto.tests.infra

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.config.addDefaultCryptoConfig
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.processors.crypto.CryptoProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.sha256Bytes
import org.junit.jupiter.api.Assertions.assertTrue

const val RESPONSE_TOPIC = "test.response"

private const val MESSAGING_CONFIGURATION_VALUE: String = """
            componentVersion="5.1"
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

private const val BOOT_CONFIGURATION = """
        instanceId=1
        topicPrefix=""
        bus.busType = INMEMORY
    """

inline fun <reified T> makeClientId(): String =
    "${T::class.java}-integration-test"

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

fun Lifecycle.isStarted() = eventually {
    assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
}

fun makeMessagingConfig(boostrapConfig: SmartConfig): SmartConfig =
    boostrapConfig.factory.create(
        ConfigFactory.parseString(MESSAGING_CONFIGURATION_VALUE)
            .withFallback(ConfigFactory.parseString(BOOT_CONFIGURATION))
    )

fun makeBootstrapConfig(extra: Map<String, SmartConfig>): SmartConfig {
    var cfg = SmartConfigFactory.create(
        ConfigFactory.parseString(
            """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
        )
    ).create(
        ConfigFactory
            .parseString(MESSAGING_CONFIGURATION_VALUE)
            .withFallback(
                ConfigFactory.parseString(BOOT_CONFIGURATION)
            )
    ).addDefaultCryptoConfig(
        fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
        fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
    )
    extra.forEach {
        cfg = cfg.withFallback(cfg.withValue(it.key, ConfigValueFactory.fromMap(it.value.root().unwrapped())))
    }
    return cfg
}

fun randomDataByteArray(): ByteArray {
    val random = Random(Instant.now().toEpochMilli())
    return random.nextBytes(random.nextInt(157, 311))
}

fun randomTenantId(): String =
    UUID.randomUUID().toString().toByteArray().sha256Bytes().toHexString().take(12)


