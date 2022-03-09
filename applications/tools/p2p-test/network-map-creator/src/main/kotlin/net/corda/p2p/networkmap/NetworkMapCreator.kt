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
import net.corda.p2p.test.HostedIdentityEntry
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.NetworkMapEntry
import net.corda.schema.TestSchema.Companion.HOSTED_MAP_TOPIC
import net.corda.schema.TestSchema.Companion.NETWORK_MAP_TOPIC
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.PublicKey
import java.util.Properties

@Suppress("SpreadOperator")
@Component(immediate = true)
class NetworkMapCreator @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : Application {

    private companion object {
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
        const val PRODUCER_CLIENT_ID = "messaging.kafka.producer.client.id"
    }

    private abstract inner class PublisherToTopic : Runnable {
        @Option(
            names = ["--kafka"], description = ["File containing Kafka connection properties."]
        )
        lateinit var kafkaPropertiesFile: File

        abstract fun getRecords(): List<Record<*, *>>

        override fun run() {
            val kafkaProperties = Properties()
            kafkaProperties.load(FileInputStream(kafkaPropertiesFile))
            if (!kafkaProperties.containsKey(KAFKA_BOOTSTRAP_SERVER)) {
                throw MappingException("No $KAFKA_BOOTSTRAP_SERVER property found in file specified via --kafka!")
            }

            val records = getRecords()

            val secretsConfig = ConfigFactory.empty()
            val bootConfig = ConfigFactory.empty()
                .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString()))
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("network-map-creator"))
            val publisherConfig = SmartConfigFactory.create(secretsConfig).create(bootConfig)
            val publisher = publisherFactory.createPublisher(PublisherConfig("network-map-creator"), publisherConfig)

            publisher.start()
            publisher.publish(records).forEach { it.get() }

            consoleLogger.info("Produced ${records.size} entries on.")
        }
    }

    @Command(
        name = "locally-hosted-map",
        showDefaultValues = true,
        description = ["Publish locally hosted identities"],
        mixinStandardHelpOptions = true,
    )
    private inner class HostingMap : PublisherToTopic() {
        @Option(
            names = ["--topic"],
            description = [
                "Topic to write the records to."
            ]
        )
        var topic: String = HOSTED_MAP_TOPIC

        @Option(names = ["--hosting-map-file"], description = ["File containing hosting map data used to populate Kafka."])
        lateinit var hostingMapFile: File
        override fun getRecords(): List<Record<*, *>> {
            val hostMapConfig = ConfigFactory.parseFile(hostingMapFile)
            val recordsWithAdditions = hostMapConfig.getConfigList("entriesToAdd").map { config ->
                val x500Name = config.getString("x500name")
                val groupId = config.getString("groupId")
                val dataConfig = config.getConfig("data")
                val tlsTenantId = dataConfig.getString("tlsTenantId")
                val identityTenantId = dataConfig.getString("identityTenantId")
                val tlsCertificates = dataConfig.getStringList("tlsCertificates").map {
                    File(it)
                }.map {
                    it.readText()
                }
                Record(
                    topic, "$x500Name-$groupId",
                    HostedIdentityEntry(
                        HoldingIdentity(x500Name, groupId),
                        tlsTenantId,
                        identityTenantId,
                        tlsCertificates
                    )
                )
            }
            val recordsWithRemovals = hostMapConfig.getConfigList("entriesToDelete").map { config ->
                val x500Name = config.getString("x500name")
                val groupId = config.getString("groupId")

                Record(topic, "$x500Name-$groupId", null)
            }
            return recordsWithAdditions + recordsWithRemovals
        }
    }
    @Command(
        name = "network-map",
        showDefaultValues = true,
        description = ["Publish network map"],
        mixinStandardHelpOptions = true,
    )
    private inner class NetworkMap : PublisherToTopic() {
        @Option(
            names = ["--topic"],
            description = [
                "Topic to write the records to."
            ]
        )
        var topic: String = NETWORK_MAP_TOPIC

        @Option(names = ["--netmap-file"], description = ["File containing network map data used to populate Kafka."])
        lateinit var networkMapFile: File

        override fun getRecords(): List<Record<*, *>> {
            val netmapConfig = ConfigFactory.parseFile(networkMapFile)
            val recordsWithAdditions = netmapConfig.getConfigList("entriesToAdd").map { config ->
                val x500Name = config.getString("x500name")
                val groupId = config.getString("groupId")
                val dataConfig = config.getConfig("data")
                val publicKeyStoreFile = dataConfig.getString("publicKeyStoreFile")
                val publicKeyAlias = dataConfig.getString("publicKeyAlias")
                val keystorePassword = dataConfig.getString("keystorePassword")
                val address = dataConfig.getString("address")
                val networkType = parseNetworkType(dataConfig.getString("networkType"))
                val trustStoreCertificates = dataConfig.getList("trustStoreCertificates")
                    .unwrapped()
                    .filterIsInstance<String>()
                    .map {
                        File(it)
                    }.map { it.readText() }

                val (keyAlgo, publicKey) = readKey(publicKeyStoreFile, publicKeyAlias, keystorePassword)
                val networkMapEntry = NetworkMapEntry(
                    HoldingIdentity(x500Name, groupId),
                    ByteBuffer.wrap(publicKey.encoded),
                    keyAlgo,
                    address,
                    networkType,
                    trustStoreCertificates,
                )
                Record(topic, "$x500Name-$groupId", networkMapEntry)
            }
            val recordsWithRemovals = netmapConfig.getConfigList("entriesToDelete").map { config ->
                val x500Name = config.getString("x500name")
                val groupId = config.getString("groupId")

                Record(topic, "$x500Name-$groupId", null)
            }
            return recordsWithAdditions + recordsWithRemovals
        }

        private fun parseNetworkType(networkType: String): NetworkType {
            return when (networkType) {
                "CORDA_4" -> NetworkType.CORDA_4
                "CORDA_5" -> NetworkType.CORDA_5
                else -> throw MappingException("Invalid network type: $networkType")
            }
        }

        private fun readKey(
            keyStoreFilePath: String,
            keyAlias: String,
            keystorePassword: String
        ): Pair<KeyAlgorithm, PublicKey> {
            val keystore = KeyStore.getInstance("JKS")
            keystore.load(FileInputStream(keyStoreFilePath), keystorePassword.toCharArray())

            val publicKey = keystore.getCertificate(keyAlias).publicKey

            val keyAlgorithm: KeyAlgorithm = when (publicKey.algorithm) {
                "RSA" -> KeyAlgorithm.RSA
                "EC" -> KeyAlgorithm.ECDSA
                else -> throw MappingException(("Invalid key algorithm value: ${publicKey.algorithm}"))
            }

            return keyAlgorithm to publicKey
        }
    }

    @Command(
        header = ["Publish network map entries or locally hosted identities"],
        name = "network-map-creator",
        mixinStandardHelpOptions = true,

    )
    private class Commands

    override fun startup(args: Array<String>) {
        consoleLogger.info("Starting network map creation tool")

        val commands = Commands()
        try {
            CommandLine(commands)
                .addSubcommand(NetworkMap())
                .addSubcommand(HostingMap())
                .execute(*args)
        } catch (e: Exception) {
            consoleLogger.warn("Could not run", e)
        } finally {
            shutdown()
        }
    }

    private class MappingException(msg: String) : Exception(msg)

    override fun shutdown() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }
}
