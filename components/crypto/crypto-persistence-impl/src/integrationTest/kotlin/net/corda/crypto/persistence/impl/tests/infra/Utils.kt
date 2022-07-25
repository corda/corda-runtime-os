package net.corda.crypto.persistence.impl.tests.infra

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import java.util.UUID

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

val vNodeHoldingIdentity = HoldingIdentity(
    "CN=Alice, O=Alice Corp, L=LDN, C=GB",
    UUID.randomUUID().toString()
)

lateinit var connectionIds: Map<String, UUID>

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
    )
    extra.forEach {
        cfg = cfg.withFallback(cfg.withValue(it.key, ConfigValueFactory.fromMap(it.value.root().unwrapped())))
    }
    return cfg
}
