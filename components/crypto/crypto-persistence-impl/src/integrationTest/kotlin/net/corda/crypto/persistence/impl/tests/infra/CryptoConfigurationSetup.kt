package net.corda.crypto.persistence.impl.tests.infra

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.time.Instant
import java.util.UUID
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.TestRandom
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro

object CryptoConfigurationSetup {

    private const val MESSAGING_CONFIGURATION_VALUE: String = """
            componentVersion="5.1"
            maxAllowedMessageSize = 1000000
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

    val boostrapConfig = makeBootstrapConfig(
        mapOf(
            BootConfig.BOOT_DB_PARAMS to CryptoDBSetup.clusterDb.config
        )
    )

    val messagingConfig = makeMessagingConfig(boostrapConfig)


    fun setup(publisher: Publisher) {
        val cryptoConfig = createDefaultCryptoConfig("passphrase", "salt").root().render()
        val virtualNodeInfo = VirtualNodeInfo(
            holdingIdentity = CryptoDBSetup.vNodeHoldingIdentity,
            cpiIdentifier = CpiIdentifier(
                name = "cpi",
                version = "1",
                signerSummaryHash = TestRandom.secureHash()
            ),
            cryptoDmlConnectionId = CryptoDBSetup.connectionId(CryptoDBSetup.vnodeDb.name),
            uniquenessDmlConnectionId = UUID.randomUUID(),
            vaultDmlConnectionId = UUID.randomUUID(),
            timestamp = Instant.now()
        )
        publisher.publish(
            listOf(
                Record(
                    Schemas.Config.CONFIG_TOPIC,
                    ConfigKeys.MESSAGING_CONFIG,
                    Configuration(
                        messagingConfig.root().render(),
                        messagingConfig.root().render(),
                        0,
                        ConfigurationSchemaVersion(1, 0)
                    )
                ),
                Record(
                    Schemas.Config.CONFIG_TOPIC,
                    ConfigKeys.CRYPTO_CONFIG,
                    Configuration(
                        cryptoConfig,
                        cryptoConfig,
                        0,
                        ConfigurationSchemaVersion(1, 0)
                    )
                ),
                Record(
                    Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                    virtualNodeInfo.holdingIdentity.toAvro(),
                    virtualNodeInfo.toAvro()
                )
            )
        )
    }

    private fun makeMessagingConfig(boostrapConfig: SmartConfig): SmartConfig =
        boostrapConfig.factory.create(
            ConfigFactory.parseString(MESSAGING_CONFIGURATION_VALUE)
                .withFallback(ConfigFactory.parseString(BOOT_CONFIGURATION))
        )

    private fun makeBootstrapConfig(extra: Map<String, SmartConfig>): SmartConfig {
        var cfg = SmartConfigFactory.createWith(
            ConfigFactory.parseString(
                """
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            ),
            listOf(EncryptionSecretsServiceFactory())
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
}
