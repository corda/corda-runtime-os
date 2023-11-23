package net.corda.db.testkit.dbutilsimpl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.schema.configuration.DatabaseConfig
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PostgresHelper : ExternalDbHelper() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun getDatabase() = getPropertyNonBlank(DBNAME_PROPERTY,"postgres")

    override fun getAdminUser() = getPropertyNonBlank(DB_ADMIN_USER_PROPERTY,"postgres")

    override fun getAdminPassword() = getPropertyNonBlank(DB_ADMIN_PASSWORD_PROPERTY, "password")

    override val port: String = getPropertyNonBlank(DBPORT_PROPERTY, "5432")

    override val jdbcUrl = "jdbc:postgresql://$host:$port/${getDatabase()}"

    override val driverClass = "org.postgresql.Driver"

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

        val jdbcUrlCopy = if (!schemaName.isNullOrBlank()) {
            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                net.corda.db.core.createDataSource(
                    driverClass,
                    jdbcUrl,
                    user,
                    password,
                    maximumPoolSize = maximumPoolSize
                ).connection.use{ conn ->
                    conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
                    conn.commit()
                }
            }

            if (rewriteBatchedInserts) {
                "$jdbcUrl?currentSchema=$schemaName&reWriteBatchedInserts=true"
            } else {
                "$jdbcUrl?currentSchema=$schemaName"
            }
        } else {
            jdbcUrl
        }
        logger.info("Using URL $jdbcUrlCopy".emphasise())
        return net.corda.db.core.createDataSource(
            driverClass,
            jdbcUrlCopy,
            user,
            password,
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
        val currentJdbcUrl = if (!schemaName.isNullOrBlank()) {
            "$jdbcUrl?currentSchema=$schemaName"
        } else {
            jdbcUrl
        }
        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(currentJdbcUrl))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }

}