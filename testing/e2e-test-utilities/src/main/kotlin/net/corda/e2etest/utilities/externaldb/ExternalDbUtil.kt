package net.corda.e2etest.utilities.externaldb

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.DriverManager

class ExternalDbUtil {

    private var postgresDb: String
    private var postgresHost: String
    private var postgresPort: String
    private var postgresUser: String
    private var postgresPassword: String
    private var jdbcUrl: String
    private var jdbcUrlCreateVNode: String
    private var postgresService: String

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)


    init {
        // Use Corda combined worker cluster DB default details if there are no external DB details
        if (System.getProperty("postgresDb").isNullOrEmpty()) {
            logger.info("Running with DB connectivity properties defaulted")
            postgresDb = "cordacluster"
            postgresHost = "localhost"
            postgresPort = "5432"
            postgresService = ""
            postgresUser = "user"
            postgresPassword = "password"
            jdbcUrl = createJdbcUrl(postgresHost, postgresPort, postgresDb)
            jdbcUrlCreateVNode = jdbcUrl
        } else {
            // Get external DB details
            logger.info("Running with external DB connectivity properties")
            postgresDb = System.getProperty("postgresDb")
            postgresHost = System.getProperty("postgresHost")
            postgresPort = System.getProperty("postgresPort")
            postgresService = System.getProperty("postgresService")
            postgresUser = System.getProperty("postgresUser")
            postgresPassword = System.getProperty("postgresPassword")
            jdbcUrl = createJdbcUrl(postgresHost, postgresPort, postgresDb)
            jdbcUrlCreateVNode = createJdbcUrl(postgresService, postgresPort, postgresDb)
        }

    }

    private fun createJdbcUrl(postgresHostname: String, postgresPort: String, postgresDb: String): String =
        "jdbc:postgresql://$postgresHostname:$postgresPort/$postgresDb"

    private fun runSql(sql: String, connectionUsernameDdl: ConnectionStatementParams? = null, password: String? = null) {
        val newJdbcUrl: String
        val connectionUser: String
        val connectionPassword: String
        if (connectionUsernameDdl != null && password != null) {
            newJdbcUrl = "${jdbcUrl}?currentSchema=${connectionUsernameDdl.currentSchema}"
            connectionUser = connectionUsernameDdl.connectionUsername.lowercase()
            connectionPassword = password
        } else {
            newJdbcUrl = jdbcUrl
            connectionUser = postgresUser
            connectionPassword = postgresPassword
        }
        DriverManager.getConnection(newJdbcUrl, connectionUser, connectionPassword).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(sql)
            }
        }
    }

    fun createUsersAndSchemas(connectionUsernames: List<ConnectionStatementParams>, password: String) {
        connectionUsernames.forEach { connectionStatementParams ->
            with(connectionStatementParams) {
                runSql("CREATE USER $connectionUsername WITH PASSWORD '$password';")
                runSql("ALTER ROLE $connectionUsername SET search_path TO ${currentSchema};")
                runSql("GRANT $schemaOwner TO current_user;")
                runSql("CREATE SCHEMA IF NOT EXISTS $currentSchema AUTHORIZATION $schemaOwner;")
                runSql("GRANT USAGE ON SCHEMA $currentSchema to $connectionUsername;")
                runSql("ALTER DEFAULT PRIVILEGES FOR ROLE $schemaOwner IN SCHEMA $currentSchema " +
                        "GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES TO $connectionUsername;")
            }
        }
        connectionUsernames.forEach { runSql("REVOKE ${it.schemaOwner} FROM current_user;") }
    }

    @Suppress("unused")
    fun changePassword(connectionUsernames: List<ConnectionStatementParams>, password: String) =
        connectionUsernames.map { "ALTER USER ${it.connectionUsername} PASSWORD '$password';" }.forEach(::runSql)


    private fun mapToExternalDBConnectionParamsJson(
        connectionUsernames: List<ConnectionStatementParams>,
        password: String
    ): List<JsonNode> {
        // Use Jackson object mapper to create a list of correctly formatted connection strings
        val mapper = jacksonObjectMapper()
        return connectionUsernames.map {
            mapper.valueToTree(
                DatabaseParameters(
                    ConnectionParameters(
                        JDBCString(jdbcUrlCreateVNode),
                        it.connectionUsername.lowercase(),
                        password
                    )
                )
            )
        }
    }

    fun mapToExternalDBConnectionParams(
        connectionUsernames: List<ConnectionStatementParams>,
        password: String
    ): List<String> {
        return mapToExternalDBConnectionParamsJson(connectionUsernames, password).map { it.toString() }
    }
}
