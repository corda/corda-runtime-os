package net.corda.cli.plugins.upgrade

import net.corda.cli.plugin.initialconfig.toInsertStatement
import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.cli.plugins.upgrade.UpgradePluginWrapper.UpgradePlugin
import net.corda.crypto.core.CryptoTenants
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.membership.datamodel.HostedIdentityEntity
import net.corda.membership.datamodel.HostedIdentitySessionKeyInfoEntity
import net.corda.membership.rest.v1.CertificateRestResource
import net.corda.membership.rest.v1.KeyRestResource
import net.corda.membership.rest.v1.types.response.KeyMetaData
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.rest.RestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.schema.Schemas
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.virtualnode.toCorda
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.reflect.KClass

@CommandLine.Command(
    name = "migrate-data",
    description = ["Read hosted identity records from Kafka and persist them to the database."],
    mixinStandardHelpOptions = true,
)
class MigrateHostedIdentities(private val output: Output = ConsoleOutput()) : RestCommand(), Runnable {

    private companion object {
        const val POLL_TIMEOUT_MS = 3000L
        const val MAX_ATTEMPTS = 10
        const val WAIT_INTERVAL = 2000L
        const val KEYS_PAGE_SIZE = 20
        val logger: Logger = LoggerFactory.getLogger(UpgradePlugin::class.java)
        const val SCHEMA_NAME = "config"
    }

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address."],
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["--kafka-config"],
        description = ["Absolute path to Kafka configuration file."]
    )
    var kafkaConfig: String? = null

    @CommandLine.Option(
        names = ["--timeout"],
        description = ["Timeout in milliseconds to read from Kafka. Defaults to 3000."]
    )
    var timeoutMs: Long = POLL_TIMEOUT_MS

    @CommandLine.Option(
        names = ["--topic-prefix"],
        description = ["Kafka topic prefix"]
    )
    var topicPrefix: String = ""

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputDir: String = "."

    private val hostedIdentityTopic by lazy {
        topicPrefix + Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
    }

    private val consumerGroup = UUID.randomUUID().toString()

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val registry = AvroSchemaRegistryImpl()
        val keyDeserializer = CordaAvroDeserializerImpl(registry, {}, String::class.java)
        val valueDeserializer = CordaAvroDeserializerImpl(registry, {}, HostedIdentityEntry::class.java)

        val consumer = KafkaConsumer(getKafkaProperties(), keyDeserializer, valueDeserializer)
        val records = try {
            consumer.subscribe(setOf(hostedIdentityTopic))
            val records = consumer.poll(Duration.ofMillis(timeoutMs)).let { records ->
                records.mapNotNull { it.value() as? HostedIdentityEntry }
            }
            records
        } catch (ex: Exception) {
            logger.warn("Failed to read hosted identity records from topic '$hostedIdentityTopic'.", ex)
            emptyList()
        } finally {
            consumer.closeConsumer()
        }
        FileWriter(File("${outputDir.removeSuffix("/")}/${SCHEMA_NAME}.sql")).use { outputFile ->
            records.forEach { persistHostedIdentity(it, outputFile) }
        }
        Thread.currentThread().contextClassLoader = contextCL
    }

    private fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        if (kafkaConfig != null) {
            kafkaProperties.load(FileInputStream(kafkaConfig!!))
        }
        kafkaProperties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServer
        kafkaProperties[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroup
        kafkaProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        return kafkaProperties
    }

    private fun KafkaConsumer<Any, Any>.closeConsumer() {
        try {
            close()
        } catch (ex: Exception) {
            UpgradePluginWrapper.logger.error("Failed to close consumer from group '$consumerGroup'.", ex)
        }
    }

    private fun findCertChainAliases(
        clusterLevelCertificate: Boolean,
        holdingId: String,
        usage: String
    ): Map<String, String> {
        val possibleTlsAliases: List<String> = createRestClient(
            CertificateRestResource::class,
            "Could not find pem data for $holdingId",
        ) { proxy ->
            if (clusterLevelCertificate) {
                proxy.getCertificateAliases(usage)
            } else {
                proxy.getCertificateAliases(usage, holdingId)
            }
        }

        return createRestClient(CertificateRestResource::class, "Could not find pem data for $holdingId") { proxy ->
            possibleTlsAliases.associate { alias ->
                if (clusterLevelCertificate) {
                    proxy.getCertificateChain(usage, alias) to alias
                } else {
                    proxy.getCertificateChain(usage, holdingId, alias) to alias
                }
            }
        }
    }

    private fun findTlsCertChainAlias(
        useClusterLevelTlsCertificateAndKey: Boolean,
        holdingId: String,
    ): Map<String, String> {
        return findCertChainAliases(useClusterLevelTlsCertificateAndKey, holdingId, "p2p-tls")
    }

    private fun findSessionCertificateAlias(holdingId: String): Map<String, String> {
        return findCertChainAliases(false, holdingId, "p2p-session")
    }

    private fun <I : RestResource, T : Any> createRestClient(
        restResource: KClass<I>,
        errorMessage: String,
        function: (proxy: I) -> T
    ): T {
        return createRestClient(restResource, RestApiVersion.C5_2).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = errorMessage,
            ) {
                try {
                    val proxy = client.start().proxy
                    val result = function(proxy)
                    client.close()
                    result
                } catch (e: ResourceNotFoundException) {
                    null
                } catch (e: ServiceUnavailableException) {
                    null
                }
            }
        }
    }

    private fun findAllSessionKeys(holdingId: String): Map<String, String> {
        // look up for all keys for holding ID
        val keyLookupResult: List<KeyMetaData> =
            createRestClient(KeyRestResource::class, "Could not find key data for $holdingId") { proxy ->
                var skip = 0
                val keys = mutableListOf<KeyMetaData>()
                do {
                    val page = proxy.listKeys(
                        holdingId,
                        skip,
                        KEYS_PAGE_SIZE,
                        "none",
                        "SESSION_INIT",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ).values.toList()
                    keys.addAll(page)
                    skip += KEYS_PAGE_SIZE
                } while (page.isNotEmpty())
                keys
            }

        val pemLookupParams = keyLookupResult.map { it.keyId }

        // find the pem format of keys for holding ID and key ID
        return createRestClient(KeyRestResource::class, "Could not find pem data for $holdingId") { proxy ->
            pemLookupParams.associateBy {
                proxy.generateKeyPem(holdingId, it)
            }
        }
    }

    private fun persistHostedIdentity(kafkaHostedIdentity: HostedIdentityEntry, file: FileWriter) {
        val holdingId = kafkaHostedIdentity.holdingIdentity.toCorda().shortHash.toString()
        val pemLookupResult = findAllSessionKeys(holdingId)

        val preferredSessionKey = pemLookupResult[kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey]
        if (preferredSessionKey == null) {
            UpgradePluginWrapper.logger.error(
                "Could not find the session key alias for ${kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey}."
            )
            return
        }
        val useClusterLevelTlsCertificateAndKey = kafkaHostedIdentity.tlsTenantId == CryptoTenants.P2P

        val tlsCertificateChainAlias = findTlsCertChainAlias(
            useClusterLevelTlsCertificateAndKey,
            holdingId
        )[kafkaHostedIdentity.tlsCertificates.joinToString("\n")]

        if (tlsCertificateChainAlias == null) {
            UpgradePluginWrapper.logger.error(
                "Could not find the TLS certificate alias for: ${kafkaHostedIdentity.tlsCertificates}."
            )
            return
        }

        val entity = HostedIdentityEntity(
            holdingId,
            preferredSessionKey,
            tlsCertificateChainAlias,
            useClusterLevelTlsCertificateAndKey,
            kafkaHostedIdentity.version
        )

        val statement = entity.toInsertStatement() + "\n"
        file.write(statement)
        val allSessionKeysAndCertificates = kafkaHostedIdentity.alternativeSessionKeysAndCerts +
            kafkaHostedIdentity.preferredSessionKeyAndCert

        val allSessionCertificates = findSessionCertificateAlias(holdingId)
        for (sessionKeyAndCert in allSessionKeysAndCertificates) {
            val sessionKeyId = pemLookupResult[kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey] ?: let {
                UpgradePluginWrapper.logger.error(
                    "Could not find the Session certificate alias for: ${kafkaHostedIdentity.tlsCertificates}."
                )
                return
            }
            val sessionCertificateAlias = sessionKeyAndCert?.sessionCertificates?.let {
                allSessionCertificates[it.joinToString("\n")]
            }
            val sessionKeyEntity = HostedIdentitySessionKeyInfoEntity(
                holdingId,
                sessionKeyId,
                sessionCertificateAlias,
            )
            val sessionKeyStatement = sessionKeyEntity.toInsertStatement() + "\n"
            file.write(sessionKeyStatement)
        }
    }
}
