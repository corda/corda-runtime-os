package net.corda.p2p.cryptoservice

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.TenantKeys
import net.corda.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

@Suppress("SpreadOperator")
@Component(immediate = true)
class CryptoServiceKeyCreator @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
        const val PRODUCER_CLIENT_ID = "messaging.kafka.producer.client.id"
    }

    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting crypto service key creation tool")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val kafkaProperties = Properties()
            val kafkaPropertiesFile = parameters.kafkaConnection
            if (kafkaPropertiesFile == null) {
                logError("No file path passed for --kafka.")
                shutdown()
                return
            }
            kafkaProperties.load(FileInputStream(kafkaPropertiesFile))
            if (!kafkaProperties.containsKey(KAFKA_BOOTSTRAP_SERVER)) {
                logError("No $KAFKA_BOOTSTRAP_SERVER property found in file specified via --kafka!")
                shutdown()
                return
            }

            if (parameters.keysConfigFile == null) {
                logError("No value passed for --keys-config.")
                shutdown()
                return
            }
            val keyPairs = readKeys(parameters.keysConfigFile!!) ?: return

            val topic = parameters.topic ?: CRYPTO_KEYS_TOPIC

            // TODO - move to common worker and pick up secrets params
            val secretsConfig = ConfigFactory.empty()
            val publisherConfig = SmartConfigFactory.create(secretsConfig).create(
                ConfigFactory.empty()
                    .withValue(
                        KAFKA_COMMON_BOOTSTRAP_SERVER,
                        ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString())
                    )
                    .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("crypto-key-creator"))
            )

            val publisher = publisherFactory.createPublisher(PublisherConfig("key-creator"), publisherConfig)

            publisher.start()
            publisher.use {
                val futures = keyPairs.map { (alias, keyPairEntry) ->
                    it.publish(listOf(Record(topic, alias, keyPairEntry)))
                }.flatten()
                futures.forEach { it.get() }
            }

            consoleLogger.info("Produced ${keyPairs.size} key entries on topic $topic.")
            shutdown()
        }
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down crypto service key creation tool")
        shutdownOSGiFramework()
        logger.info("Shutting down crypto service key creation tool")
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    private fun logError(error: String) {
        logger.error(error)
        consoleLogger.error(error)
    }

    private fun getAlgorithm(publicKey: PublicKey): KeyAlgorithm? {
        return when (publicKey.algorithm) {
            "RSA" -> KeyAlgorithm.RSA
            "EC" -> KeyAlgorithm.ECDSA
            else -> {
                logger.error("Algorithm ${publicKey.algorithm} not supported")
                return null
            }
        }
    }

    private fun readEntry(config: Config): Pair<String, TenantKeys>? {
        val publishAlias = try {
            config.getString("publishAlias")
        } catch (_: ConfigException.Missing) {
            UUID.randomUUID().toString()
        }
        val keystoreFilePath = config.getString("keystoreFile")
        val keystorePassword = config.getString("password")

        val keystore = KeyStore.getInstance("JKS")
        keystore.load(FileInputStream(keystoreFilePath), keystorePassword.toCharArray())
        val keyStoreAlias = try {
            config.getString("keystoreAlias")
        } catch (_: ConfigException.Missing) {
            keystore.aliases().nextElement()
        }

        val privateKey = keystore.getKey(keyStoreAlias, keystorePassword.toCharArray()) as PrivateKey
        val publicKey = keystore.getCertificate(keyStoreAlias).publicKey

        val keyAlgorithm = getAlgorithm(publicKey) ?: return null

        val tenantId = config.getString("tenantId")

        return publishAlias to
            TenantKeys(
                tenantId,
                KeyPairEntry(
                    keyAlgorithm,
                    ByteBuffer.wrap(publicKey.encoded),
                    ByteBuffer.wrap(privateKey.encoded)
                ),
            )
    }

    private fun readKeys(keysConfigFile: File): List<Pair<String, TenantKeys>>? {
        val keysConfig = ConfigFactory.parseFile(keysConfigFile)
        return keysConfig.getConfigList("keys").map { config ->
            val entry = readEntry(config)
            if (entry == null) {
                shutdown()
                return null
            }
            entry
        }
    }
}

class CliParameters {
    @CommandLine.Option(
        names = ["--kafka"], description = ["File containing Kafka connection properties."]
    )
    var kafkaConnection: File? = null

    @CommandLine.Option(
        names = ["--topic"],
        description = [
            "Topic to write the records to. " +
                "Defaults to $CRYPTO_KEYS_TOPIC, if not specified."
        ]
    )
    var topic: String? = null

    @CommandLine.Option(names = ["--keys-config"], description = ["File containing key metadata used to populate Kafka."])
    var keysConfigFile: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}
