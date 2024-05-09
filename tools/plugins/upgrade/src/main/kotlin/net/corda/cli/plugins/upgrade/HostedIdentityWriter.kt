package net.corda.cli.plugins.upgrade

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.cli.plugins.network.utils.PrintUtils.printJsonOutput
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.membership.datamodel.HostedIdentityEntity
import net.corda.membership.rest.v1.KeyRestResource
import net.corda.membership.rest.v1.types.response.KeyMetaData
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import picocli.CommandLine
import java.sql.DriverManager
import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Table
import javax.persistence.Version
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

// get db password:
// kubectl get secret --namespace nikolettnagy-cluster-b prereqs-postgresql -o jsonpath="{.data.postgres-password}" | base64 --decode
// to run the command:
// ./corda-cli.sh upgrade write-db --jdbc-url=jdbc:postgresql://localhost:5434/cordacluster
// --db-user postgres --db-password y9ZZ91hIvb -t https://localhost:8888 -u admin -p admin --insecure
@CommandLine.Command(
    name = "write-db",
    description = ["Write hosted identity records to the DB"],
    mixinStandardHelpOptions = true,
)
class HostedIdentityWriter(private val output: Output = ConsoleOutput()) : RestCommand(), Runnable {
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

    private companion object {
        const val MAX_ATTEMPTS = 10
        const val WAIT_INTERVAL = 2000L
    }

    override fun run() {
        Class.forName("org.postgresql.Driver")
        println("Ran command: 'upgrade' subcommand: 'write-db'.")
        val connection = connectToDatabase()
        val testId = "FB04950F5F92"

        // look up for all keys for holding ID
        val keyLookupResult: Pair<String, List<KeyMetaData>> = createRestClient(KeyRestResource::class, RestApiVersion.C5_2).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Could not find key data for $testId",
            ) {
                try {
                    val proxy = client.start().proxy
                    val keys = testId to proxy.listKeys(
                        testId,
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
        val pemLookupResult: List<Triple<String, String, String>> = createRestClient(
            KeyRestResource::class,
            RestApiVersion.C5_2
        ).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Could not find pem data for $testId",
            ) {
                try {
                    val proxy = client.start().proxy
                    val result = pemLookupParams.second.map {
                        val pem = proxy.generateKeyPem(pemLookupParams.first, it)
                        Triple(pemLookupParams.first, it, pem)
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

        // Should map the PEM to find the ID for preferred key ID
        // Need to figure out the cert alias, but possibly `p2p-tls-cert` everywhere?
        // Boolean should be something like `identity.tlsTenantId == CryptoTenants.P2P`? but needs to be verified
        // Default version to 1
        val entity = HostedIdentityEntity(
            testId,
            pemLookupResult.first().second,
            "dummyAlias",
            true,
            2
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
