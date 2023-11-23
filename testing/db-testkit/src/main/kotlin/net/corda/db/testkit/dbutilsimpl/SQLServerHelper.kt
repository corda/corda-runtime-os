package net.corda.db.testkit.dbutilsimpl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.schema.configuration.DatabaseConfig
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.InvalidParameterException
import java.sql.Connection

class SQLServerHelper : ExternalDbHelper() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

      override fun getDatabase() = getPropertyNonBlank(DBNAME_PROPERTY, "master")

    override fun getAdminUser() = getPropertyNonBlank(DB_ADMIN_USER_PROPERTY, "sa")

    override fun getAdminPassword() = getPropertyNonBlank(DB_ADMIN_PASSWORD_PROPERTY, "yourStrong(!)Password")

    override val port: String = getPropertyNonBlank(DBPORT_PROPERTY, "1433")

    override val jdbcUrl : String
        get() {
            val databaseConfig = if (getDatabase().isBlank()) "" else "database=${getDatabase()};"
            return "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;$databaseConfig"
        }

    override val driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver"

    fun createSchemaAndLogin(
        connection: Connection,
        schemaName: String
    ): Pair<String, String> {
        val schemaUser = "user_$schemaName"
        val schemaPassword = "password${schemaName}123(!)"
        connection.use { conn ->
                conn.prepareStatement("""
                    IF NOT EXISTS ( SELECT  * FROM sys.schemas WHERE   name = N'$schemaName' )
                        BEGIN
                        EXEC('CREATE SCHEMA [$schemaName]');
                        CREATE LOGIN $schemaUser WITH PASSWORD = '$schemaPassword'
                        CREATE USER $schemaUser FOR LOGIN $schemaUser
                        ALTER USER $schemaUser WITH DEFAULT_SCHEMA = $schemaName
                        GRANT ALTER, INSERT, DELETE, SELECT, UPDATE ON SCHEMA :: $schemaName to $schemaUser
                        GRANT CREATE TABLE TO $schemaUser
                    END
                     """.trimIndent()).execute()
                conn.commit()
        }
        return schemaUser to schemaPassword
    }

    override fun createDataSource(
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        rewriteBatchedInserts: Boolean,
        maximumPoolSize: Int
    ): CloseableDataSource {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        var credentials = user to password

        if (!schemaName.isNullOrBlank()) {
            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                credentials = createSchemaAndLogin(
                    net.corda.db.core.createDataSource(
                        driverClass,
                        jdbcUrl,
                        user,
                        password,
                        maximumPoolSize = maximumPoolSize
                    ).connection, schemaName
                )
            }
            else if (dbUser.isNullOrBlank()){
                throw InvalidParameterException("MS Sqlserver requires a schema specific user - if you set a schema," +
                        " you must created a user for it.")
            }
        }

        logger.info("Using URL $jdbcUrl".emphasise())
        return net.corda.db.core.createDataSource(
            driverClass,
            jdbcUrl,
            credentials.first,
            credentials.second,
            maximumPoolSize = maximumPoolSize
        )
    }

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        // This needs more thought: what do we do with the schema? MS SQL maps schemas to users, not by URL
        if (!schemaName.isNullOrBlank() and dbUser.isNullOrBlank()){
            throw InvalidParameterException("MS Sqlserver requires a schema specific user - if you set a schema," +
                    " you must created a user for it.")
        }
        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }

}