package net.corda.cli.plugins.upgrade

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.Schemas
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import picocli.CommandLine
import java.io.FileInputStream
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Table
import javax.persistence.Version
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.cli.plugins.network.utils.PrintUtils.printJsonOutput
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.crypto.core.CryptoTenants
import net.corda.data.certificates.CertificateUsage
import net.corda.membership.datamodel.HostedIdentityEntity
import net.corda.membership.rest.v1.CertificateRestResource
import net.corda.membership.rest.v1.KeyRestResource
import net.corda.membership.rest.v1.types.response.KeyMetaData
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

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
    }

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address."],
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["-k", "--kafka-config"],
        description = ["Absolute path to Kafka configuration file."]
    )
    var kafkaConfig: String? = null

    @CommandLine.Option(
        names = ["-t", "--timeout"],
        description = ["Timeout in milliseconds to read from Kafka. Defaults to 3000."]
    )
    var timeoutMs: Long = POLL_TIMEOUT_MS

    @CommandLine.Option(
        names = ["-p", "--topic-prefix"],
        description = ["Kafka topic prefix"]
    )
    var topicPrefix: String = ""

    @CommandLine.Option(
        names = ["--jdbc-url"],
        description = ["JDBC Url of database. If not specified runs in offline mode"]
    )
    var jdbcUrl: String? = null

    @CommandLine.Option(
        names = ["--db-user"],
        description = ["Database username"]
    )
    var dbUser: String? = null

    @CommandLine.Option(
        names = ["--db-password"],
        description = ["Database password"]
    )
    var dbPassword: String? = null

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
                UpgradePluginWrapper.logger.debug("Read {} records from topic '{}'.", records.count(), hostedIdentityTopic)
                records.mapNotNull { it.value() as? HostedIdentityEntry }
            }
            UpgradePluginWrapper.logger.debug("Read the following records from topic '{}': {}.", hostedIdentityTopic, records)

            records
        } catch (ex: Exception) {
            UpgradePluginWrapper.logger.warn("Failed to read hosted identity records from topic '$hostedIdentityTopic'.", ex)
            emptyList()
        } finally {
            consumer.closeConsumer()
        }

        records.forEach { persistHostedIdentity(it) }
        // TODO to be replaced with persistence logic
        println("Read the following records from topic '$hostedIdentityTopic': $records.")

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

    private data class KeyLookupResult(
        val tenantId: String,
        val keyId: String,
        val pem: String
    )

    private fun persistHostedIdentity(kafkaHostedIdentity: HostedIdentityEntry) {
        Class.forName("org.postgresql.Driver")
        val connection = connectToDatabase()
        val holdingId = kafkaHostedIdentity.holdingIdentity.toCorda().shortHash.toString()

        // look up for all keys for holding ID
        val keyLookupResult: Pair<String, List<KeyMetaData>> = createRestClient(KeyRestResource::class, RestApiVersion.C5_2).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Could not find key data for $holdingId",
            ) {
                try {
                    val proxy = client.start().proxy
                    val keys = holdingId to proxy.listKeys(
                        holdingId,
                        0,
                        20,
                        "none",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ).values.filter { it.hsmCategory == "SESSION_INIT" }
                    keys
                } catch (e: ResourceNotFoundException) {
                    null
                } catch (e: ServiceUnavailableException) {
                    null
                }
            }
        }

        val pemLookupParams = keyLookupResult.first to keyLookupResult.second.map { it.keyId }

        // find the pem format of keys for holding ID and key ID
        val pemLookupResult: List<KeyLookupResult> = createRestClient(
            KeyRestResource::class,
            RestApiVersion.C5_2
        ).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Could not find pem data for $holdingId",
            ) {
                try {
                    val proxy = client.start().proxy
                    val result = pemLookupParams.second.map {
                        val pem = proxy.generateKeyPem(pemLookupParams.first, it)
                        KeyLookupResult(pemLookupParams.first, it, pem)
                    }
                    result
                } catch (e: ResourceNotFoundException) {
                    null
                } catch (e: ServiceUnavailableException) {
                    null
                }
            }
        }

        verifyAndPrintError {
            printJsonOutput(keyLookupResult, output)
            printJsonOutput(pemLookupResult, output)
        }

        val preferredSessionKey = pemLookupResult.single {
            it.pem == kafkaHostedIdentity.preferredSessionKeyAndCert.sessionPublicKey
        }.keyId

        val useClusterLevelTlsCertificateAndKey = kafkaHostedIdentity.tlsTenantId == CryptoTenants.P2P

        val possibleTlsAliases: List<String> = createRestClient(
            CertificateRestResource::class,
            RestApiVersion.C5_2
        ).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Could not find pem data for $holdingId",
            ) {
                try {
                    val proxy = client.start().proxy
                    if (useClusterLevelTlsCertificateAndKey) {
                        proxy.getCertificateAliases(CertificateUsage.P2P_TLS.toString())
                    } else {
                        proxy.getCertificateAliases(CertificateUsage.P2P_TLS.toString(), holdingId)
                    }
                } catch (e: ResourceNotFoundException) {
                    null
                } catch (e: ServiceUnavailableException) {
                    null
                }
            }
        }

        val tlsCertificateChainAlias = createRestClient(
            CertificateRestResource::class,
            RestApiVersion.C5_2
        ).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Could not find pem data for $holdingId",
            ) {
                try {
                    val proxy = client.start().proxy
                    possibleTlsAliases.map { alias ->
                        alias to proxy.getCertificateChain(CertificateUsage.P2P_TLS.toString(), alias)
                    }
                } catch (e: ResourceNotFoundException) {
                    null
                } catch (e: ServiceUnavailableException) {
                    null
                }
            }
        }.first {
            it.second == kafkaHostedIdentity.tlsCertificates.joinToString("\n")
        }.first


        // Should map the PEM to find the ID for preferred key ID
        // Need to figure out the cert alias, but possibly `p2p-tls-cert` everywhere?
        // Boolean should be something like `identity.tlsTenantId == CryptoTenants.P2P`? but needs to be verified
        // Default version to 1
        val entity = HostedIdentityEntity(
            holdingId,
            preferredSessionKey,
            tlsCertificateChainAlias,
            useClusterLevelTlsCertificateAndKey,
            kafkaHostedIdentity.version
        )

        // need to add insert for hosted_identity_session_key_info table as well
        val statement = entity.toInsertStatement()
        connection.createStatement().execute(statement)
    }

    /*private fun insertHostedIdentities(identities: List<HostedIdentityEntry>) {
        identities.forEach { identity ->
            identity.
            val entity = HostedIdentityEntity(
                identity.holdingIdentity.toCorda().shortHash.value,
                "asd",
                "asd",
                identity.tlsTenantId == CryptoTenants.P2P,
                1
            )
        }
    }*/

    private fun connectToDatabase() =
        DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)

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
