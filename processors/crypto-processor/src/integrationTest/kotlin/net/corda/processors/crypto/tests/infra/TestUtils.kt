package net.corda.processors.crypto.tests.infra

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import java.time.Instant
import kotlin.random.Random

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

private val smartConfigFactory: SmartConfigFactory =
    SmartConfigFactory.createWith(
    ConfigFactory.parseString(
        """
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
    ),
        listOf(EncryptionSecretsServiceFactory())
)

inline fun <reified T> makeClientId(): String =
    "${T::class.java}-integration-test"

fun makeMessagingConfig(): SmartConfig =
    smartConfigFactory.create(
        ConfigFactory.parseString(MESSAGING_CONFIGURATION_VALUE)
            .withFallback(ConfigFactory.parseString(BOOT_CONFIGURATION))
    )

fun makeBootstrapConfig(dbParams: SmartConfig): SmartConfig = smartConfigFactory.create(
    ConfigFactory
        .parseString(MESSAGING_CONFIGURATION_VALUE)
        .withFallback(ConfigFactory.parseString(BOOT_CONFIGURATION))
        .withFallback(
            ConfigFactory.parseMap(
                mapOf(
                    BOOT_CRYPTO to createCryptoBootstrapParamsMap(SOFT_HSM_ID)
                )
            )
        )
        .withFallback(
            ConfigFactory.parseMap(
                mapOf(
                    BOOT_DB_PARAMS to dbParams.root().unwrapped()
                )
            )
        )
)

fun makeCryptoConfig(): Config = createDefaultCryptoConfig("master-key-pass", "master-key-salt")

fun randomDataByteArray(): ByteArray {
    val random = Random(Instant.now().toEpochMilli())
    return random.nextBytes(random.nextInt(157, 311))
}

fun Publisher.publishVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo) {
    publish(
        listOf(
            Record(
                Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                virtualNodeInfo.holdingIdentity.toAvro(),
                virtualNodeInfo.toAvro()
            )
        )
    )
}

