package net.corda.p2p.networkmap

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.p2p.NetworkType
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.NetworkMapEntry
import net.corda.schema.TestSchema.Companion.NETWORK_MAP_TOPIC
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
import java.security.PublicKey
import java.util.*

@Suppress("SpreadOperator")
@Component(immediate = true)
class NetworkMapCreator @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
): Application {

    private companion object {
        private val logger: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
        const val PRODUCER_CLIENT_ID = "messaging.kafka.producer.client.id"
    }

    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting network map creation tool")

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

            if (parameters.networkMapFile == null) {
                logError("No value passed for --netmap-file.")
                shutdown()
                return
            }

            val topic = parameters.topic ?: NETWORK_MAP_TOPIC
            val netmapConfig = ConfigFactory.parseFile(parameters.networkMapFile)
            val recordsWithAdditions = netmapConfig.getConfigList("entriesToAdd").map { config ->
                val x500Name = config.getString("x500name")
                val groupId = config.getString("groupId")
                val dataConfig = config.getConfig("data")
                val publicKeyStoreFile = dataConfig.getString("publicKeyStoreFile")
                val publicKeyAlias = dataConfig.getString("publicKeyAlias")
                val keystorePassword = dataConfig.getString("keystorePassword")
                val publicKeyAlgo = dataConfig.getString("publicKeyAlgo")
                val address = dataConfig.getString("address")
                val networkType = parseNetworkType(dataConfig.getString("networkType"))
                val trustStoreCertificates = dataConfig.getList("dataConfig")
                    .unwrapped()
                    .filterIsInstance<String>()
                    .map {
                    File(it)
                }.map { it.readText() }

                val (keyAlgo, publicKey) = readKey(publicKeyStoreFile, publicKeyAlgo, publicKeyAlias, keystorePassword)
                val networkMapEntry = NetworkMapEntry(
                    HoldingIdentity(x500Name, groupId),
                    ByteBuffer.wrap(publicKey.encoded),
                    keyAlgo,
                    address,
                    networkType,
                    trustStoreCertificates
                )
                Record(topic, "$x500Name-$groupId", networkMapEntry)
            }
            val recordsWithRemovals = netmapConfig.getConfigList("entriesToDelete").map { config ->
                val x500Name = config.getString("x500name")
                val groupId = config.getString("groupId")

                Record(topic, "$x500Name-$groupId", null)
            }
            val totalRecords = recordsWithAdditions + recordsWithRemovals

            // TODO - pick up secrets params from startup
            val secretsConfig = ConfigFactory.empty()
            val bootConfig = ConfigFactory.empty()
                .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString()))
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("network-map-creator"))
            val publisherConfig = SmartConfigFactory.create(secretsConfig).create(bootConfig)
            val publisher = publisherFactory.createPublisher(PublisherConfig("network-map-creator"), publisherConfig)

            publisher.start()
            publisher.publish(totalRecords).forEach { it.get() }

            consoleLogger.info("Produced ${totalRecords.size} entries on topic $topic.")
            shutdown()
        }
    }

    override fun shutdown() {
        consoleLogger.info("Shutting down network map creation tool")
        shutdownOSGiFramework()
        logger.info("Shutting down network map creation tool")
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    private fun logError(error: String) {
        logger.error(error)
        consoleLogger.error(error)
    }

    private fun parseNetworkType(networkType: String): NetworkType {
        val parsedNetworkType = when(networkType) {
            "CORDA_4" -> NetworkType.CORDA_4
            "CORDA_5" -> NetworkType.CORDA_5
            else -> null
        }

        if (parsedNetworkType == null) {
            logError("Invalid network type: $networkType")
            shutdown()
        }

        return parsedNetworkType!!
    }

    private fun readKey(keyStoreFilePath: String,
                        keyAlgo: String, keyAlias:
                        String, keystorePassword: String): Pair<KeyAlgorithm, PublicKey> {
        val keystore = KeyStore.getInstance("JKS")
        keystore.load(FileInputStream(keyStoreFilePath), keystorePassword.toCharArray())

        val publicKey = keystore.getCertificate(keyAlias).publicKey

        val keyAlgorithm: KeyAlgorithm? = when (keyAlgo) {
            "RSA" -> KeyAlgorithm.RSA
            "ECDSA" -> KeyAlgorithm.ECDSA
            else -> {
                null
            }
        }

        if (keyAlgorithm == null) {
            logError("Invalid key algorithm value: $keyAlgo")
            shutdown()
        }

        return keyAlgorithm!! to publicKey
    }
}

class CliParameters {
    @CommandLine.Option(
        names = ["--kafka"], description = ["File containing Kafka connection properties."]
    )
    var kafkaConnection: File? = null

    @CommandLine.Option(names = ["--topic"], description = ["Topic to write the records to. " +
            "Defaults to ${NETWORK_MAP_TOPIC}, if not specified."])
    var topic: String? = null

    @CommandLine.Option(names = ["--netmap-file"], description = ["File containing network map data used to populate Kafka."])
    var networkMapFile: File? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}