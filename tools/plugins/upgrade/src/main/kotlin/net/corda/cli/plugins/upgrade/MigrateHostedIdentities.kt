package net.corda.cli.plugins.upgrade

import net.corda.cli.plugin.initialconfig.toInsertStatement
import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.crypto.core.CryptoTenants
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
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
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import net.corda.virtualnode.toCorda
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.ExitCode
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import kotlin.reflect.KClass

@CommandLine.Command(
    name = "migrate-data-5-2-1",
    description = ["Read hosted identity records from Kafka and generate SQL to persist them to the database."],
    mixinStandardHelpOptions = true,
)
class MigrateHostedIdentities : RestCommand(), Callable<Int> {

    private companion object {
        const val POLL_TIMEOUT_MS = 3000L
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_ATTEMPTS = 10
        const val WAIT_INTERVAL = 2000L
        const val KEYS_PAGE_SIZE = 20
        const val SQL_FILE_NAME = "migrate_member_data"
    }

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address."],
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["--kafka-config"],
        description = ["Path to Kafka configuration file."]
    )
    var kafkaConfig: String? = null

    @CommandLine.Option(
        names = ["--timeout"],
        description = ["Timeout in milliseconds to read from Kafka. Defaults to 3000."]
    )
    var timeoutMs: Long = POLL_TIMEOUT_MS

    @CommandLine.Option(
        names = ["-n", "--name-prefix"],
        description = ["Name prefix for topics"]
    )
    var namePrefix: String = ""

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputDir: String = "."

    private val hostedIdentityTopic by lazy {
        namePrefix + Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
    }

    private val sysOut by lazy {
        LoggerFactory.getLogger("SystemOut")
    }

    private val oldHostedIdentityEntrySchema = Schema.Parser()
        .addTypes(
            mapOf(
                HoldingIdentity::class.java.name to HoldingIdentity.`SCHEMA$`,
                HostedIdentitySessionKeyAndCert::class.java.name to HostedIdentitySessionKeyAndCert.`SCHEMA$`
            )
        ).parse(
            """
        {
          "type": "record",
          "name": "HostedIdentityEntry",
          "namespace": "net.corda.data.p2p",
          "fields": [
            {
              "doc": "The Holding identity hosted in this node",
              "name": "holdingIdentity",
              "type": "net.corda.data.identity.HoldingIdentity"
            },
            {
              "doc": "The tenant ID under which the TLS key is stored",
              "name": "tlsTenantId",
              "type": "string"
            },
            {
              "doc": "The TLS certificates (in PEM format)",
              "name": "tlsCertificates",
              "type": {
                "type": "array",
                "items": "string"
              }
            },
            {
              "doc": "The preferred session initiation key and certificate",
              "name": "preferredSessionKeyAndCert",
              "type": "HostedIdentitySessionKeyAndCert"
            },
            {
              "doc": "Alternative session initiation keys and certificates",
              "name": "alternativeSessionKeysAndCerts",
               "type": {
                 "type": "array",
                 "items": "HostedIdentitySessionKeyAndCert"
               }
             }
          ]
        }
            """.trimIndent()
        )

    private val consumerGroup = UUID.randomUUID().toString()

    override fun call(): Int {
        val consumer = executeWithThreadContextClassLoader(this::class.java.classLoader) {
            val registry = AvroSchemaRegistryImpl()
            registry.addSchemaOnly(oldHostedIdentityEntrySchema)
            val keyDeserializer = CordaAvroDeserializerImpl(registry, {}, String::class.java)
            val valueDeserializer = CordaAvroDeserializerImpl(registry, {}, HostedIdentityEntry::class.java)
            KafkaConsumer(getKafkaProperties(), keyDeserializer, valueDeserializer)
        }
        val allRecords = mutableListOf<HostedIdentityEntry>()
        try {
            consumer.subscribe(setOf(hostedIdentityTopic))
            do {
                val records = consumer.poll(Duration.ofMillis(timeoutMs)).let { records ->
                    logger.debug("Read {} records from topic '{}'.", records.count(), hostedIdentityTopic)
                    records.map { record ->
                        val hostedIdentityEntity = record.value() as? HostedIdentityEntry
                        if (hostedIdentityEntity == null) {
                            logger.error("Could not deserialize ${HostedIdentityEntity::class.java}.")
                            return ExitCode.SOFTWARE
                        }
                        hostedIdentityEntity
                    }
                }
                consumer.commitSync()
                logger.trace("Read the following records from topic '{}': {}.", hostedIdentityTopic, records)
                if (records.isNotEmpty()) {
                    allRecords.addAll(records)
                }
            } while (records.isNotEmpty())
        } catch (ex: Exception) {
            logger.warn("Failed to read hosted identity records from topic '$hostedIdentityTopic'.", ex)
            return ExitCode.SOFTWARE
        } finally {
            consumer.closeConsumer()
        }
        FileWriter(File("${outputDir.removeSuffix("/")}/${SQL_FILE_NAME}.sql")).use { outputFile ->
            allRecords.forEach {
                val success = persistHostedIdentity(it, outputFile)
                if (success != ExitCode.OK) {
                    return success
                }
            }
        }
        return ExitCode.OK
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
            logger.error("Failed to close consumer from group '$consumerGroup'.", ex)
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

    private fun persistHostedIdentity(kafkaHostedIdentity: HostedIdentityEntry, file: FileWriter): Int {
        val holdingId = kafkaHostedIdentity.holdingIdentity.toCorda().shortHash.toString()
        val pemLookupResult = findAllSessionKeys(holdingId)

        val preferredSessionKey = pemLookupResult[kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey]
        if (preferredSessionKey == null) {
            UpgradePluginWrapper.logger.error(
                "Could not find the session key alias for ${kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey}."
            )
            return ExitCode.SOFTWARE
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
            return ExitCode.SOFTWARE
        }

        val entity = HostedIdentityEntity(
            holdingId,
            preferredSessionKey,
            tlsCertificateChainAlias,
            useClusterLevelTlsCertificateAndKey,
            kafkaHostedIdentity.version ?: 1
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
                return ExitCode.SOFTWARE
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
        return ExitCode.OK
    }
}
