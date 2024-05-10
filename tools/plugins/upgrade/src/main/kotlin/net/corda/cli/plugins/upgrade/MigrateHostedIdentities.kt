package net.corda.cli.plugins.upgrade

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
import java.io.FileInputStream
import java.time.Duration
import java.util.Properties
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Table
import javax.persistence.Version
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

@Suppress("TooManyFunctions")
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

        records.forEach { persistHostedIdentity(it) }
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

    private fun findCertChainAlias(
        clusterLevelCertificate: Boolean,
        holdingId: String,
        certificates: List<String>,
        usage: String
    ): String {
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
            possibleTlsAliases.map { alias ->
                if (clusterLevelCertificate) {
                    alias to proxy.getCertificateChain(usage, alias)
                } else {
                    alias to proxy.getCertificateChain(usage, holdingId, alias)
                }
            }
        }.first {
            it.second == certificates.joinToString("\n")
        }.first
    }

    private fun findTlsCertChainAlias(
        useClusterLevelTlsCertificateAndKey: Boolean,
        holdingId: String,
        tlsCertificates: List<String>
    ): String {
        return findCertChainAlias(useClusterLevelTlsCertificateAndKey, holdingId, tlsCertificates, "p2p-tls")
    }

    private fun findSessionCertificateAlias(holdingId: String, sessionCertificates: List<String>): String {
        return findCertChainAlias(false, holdingId, sessionCertificates, "p2p-session")
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

    private fun persistHostedIdentity(kafkaHostedIdentity: HostedIdentityEntry) {
        val holdingId = kafkaHostedIdentity.holdingIdentity.toCorda().shortHash.toString()
        val pemLookupResult = findAllSessionKeys(holdingId)

        val preferredSessionKey = pemLookupResult[kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey]
            ?: throw IllegalArgumentException()

        val useClusterLevelTlsCertificateAndKey = kafkaHostedIdentity.tlsTenantId == CryptoTenants.P2P

        val tlsCertificateChainAlias =
            findTlsCertChainAlias(useClusterLevelTlsCertificateAndKey, holdingId, kafkaHostedIdentity.tlsCertificates)

        val entity = HostedIdentityEntity(
            holdingId,
            preferredSessionKey,
            tlsCertificateChainAlias,
            useClusterLevelTlsCertificateAndKey,
            kafkaHostedIdentity.version
        )

        val statement = entity.toInsertStatement()
        print(statement)
        // connection.createStatement().execute(statement)
        val allSessionKeysAndCertificates = kafkaHostedIdentity.alternativeSessionKeysAndCerts +
            kafkaHostedIdentity.preferredSessionKeyAndCert

        for (sessionKeyAndCert in allSessionKeysAndCertificates) {
            val sessionKeyId = pemLookupResult[kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey] ?: let {
                throw IllegalArgumentException()
            }
            val sessionCertificateAlias = sessionKeyAndCert?.sessionCertificates?.let {
                findSessionCertificateAlias(holdingId, it)
            }
            val sessionKeyEntity = HostedIdentitySessionKeyInfoEntity(
                holdingId,
                sessionKeyId,
                sessionCertificateAlias,
            )
            val sessionKeyStatement = sessionKeyEntity.toInsertStatement()
            print(sessionKeyStatement)
        }
    }

    private fun Any.toInsertStatement(): String {
        val values = this::class.declaredMemberProperties.mapNotNull { property ->
            val columnInfo = getColumnInfo(property) ?: return@mapNotNull null
            property.isAccessible = true
            val value = formatValue(extractValue(property, this, columnInfo.joinColumn)) ?: return@mapNotNull null
            columnInfo.name to value
        }

        return "insert into config.${formatTableName(this)} (${values.joinToString { it.first }}) " +
            "values (${values.joinToString { it.second }});"
    }

    private fun formatValue(value: Any?): String? {
        return when (value) {
            null -> null
            is Short, is Int, is Long, is Float, is Double -> value.toString()
            is Boolean -> value.toString()
            is String -> "'${value.simpleSqlEscaping()}'"
            else -> "'${value.toString().simpleSqlEscaping()}'"
        }
    }

    private data class ColumnInfo(val name: String, val joinColumn: Boolean)

    private fun getColumnInfo(property: KProperty1<out Any, *>): ColumnInfo? {
        property.getVarAnnotation(Column::class.java)?.name?.let { name ->
            return if (name.isBlank()) {
                ColumnInfo(property.name, false)
            } else {
                ColumnInfo(name, false)
            }
        }
        property.getVarAnnotation(JoinColumn::class.java)?.name?.let { name ->
            return if (name.isBlank()) {
                ColumnInfo(property.name, true)
            } else {
                ColumnInfo(name, true)
            }
        }
        property.getVarAnnotation(Id::class.java)?.let {
            return ColumnInfo(property.name, false)
        }
        return property.getVarAnnotation(Version::class.java)?.let {
            ColumnInfo("version", false)
        }
    }

    private fun <T : Annotation> KProperty1<*, *>.getVarAnnotation(type: Class<T>): T? {
        return (javaField?.getAnnotation(type) ?: javaGetter?.getAnnotation(type))?.also {
            if (this !is KMutableProperty1<*, *>) {
                throw IllegalArgumentException("Property '$this' must be var for JPA annotations.")
            }
        }
    }

    private fun String.simpleSqlEscaping(): String {
        val output = StringBuilder()
        var i = 0
        for (c in this) {
            output.append(
                when (c) {
                    '\'' -> "\\'"
                    // This prevents escaping backslash if it is part of already escaped double quotes
                    '\\' -> if (this.length > i + 1 && this[i + 1] == '\"') { "\\" } else { "\\\\" }
                    else -> c
                }
            )
            i++
        }
        return output.toString()
    }

    private fun extractValue(field: KProperty1<out Any?, *>, obj: Any, getId: Boolean): Any? {
        val value = field.getter.call(obj)
        if (!getId || value == null) {
            return value
        }
        value::class.declaredMemberProperties.forEach { property ->
            if (property.getVarAnnotation(Id::class.java) != null) {
                property.isAccessible = true
                return property.getter.call(value)
            }
        }
        throw IllegalArgumentException("Value ${value::class.qualifiedName} for join column does not have a primary key/id column")
    }

    private fun formatTableName(entity: Any): String {
        val table = entity::class.annotations.find { it is Table } as? Table
            ?: throw IllegalArgumentException("Can't create SQL from ${entity::class.qualifiedName}, it is not a persistence entity")

        val schema = table.schema.let { if (it.isBlank()) "" else "$it." }
        return table.name.let { name ->
            if (name.isBlank()) {
                "$schema${entity::class.simpleName}"
            } else {
                "$schema$name"
            }
        }
    }
}
