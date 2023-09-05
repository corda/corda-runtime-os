package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DBBaseDataSourceFactory
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.schema.configuration.DatabaseConfig
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PostgresHelper : DbUtilsHelper{
    companion object {
        private const val POSTGRES_PORT_PROPERTY = "postgresPort"
        private const val POSTGRES_HOST_PROPERTY = "postgresHost"
    }

    override fun isInMemory() = false

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun getDatabase() = getPropertyNonBlank("postgresDb","postgres")

    override fun getAdminUser() = getPropertyNonBlank("postgresUser","postgres")

    override fun getAdminPassword() = getPropertyNonBlank("postgresPassword", "password")

    override fun getEntityManagerConfiguration(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        showSql: Boolean,
        rewriteBatchedInserts: Boolean
    ): EntityManagerConfiguration {
        val ds = createDataSource(dbUser,dbPassword, schemaName, createSchema, rewriteBatchedInserts)
        return DbEntityManagerConfiguration(ds,showSql,true, DdlManage.NONE)
    }

    override fun createDataSource(
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        rewriteBatchedInserts: Boolean
    ): CloseableDataSource {
        val port = System.getProperty(POSTGRES_PORT_PROPERTY)
        val postgresDb = getDatabase()
        val host = getPropertyNonBlank(POSTGRES_HOST_PROPERTY,"localhost")
        var jdbcUrl = "jdbc:postgresql://$host:$port/$postgresDb"

        val factory = DBBaseDataSourceFactory()

        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()

        if (!schemaName.isNullOrBlank()) {
            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                factory.create("org.postgresql.Driver",jdbcUrl, user, password, maximumPoolSize = 1).connection.createSchema(schemaName)
            }
            jdbcUrl = if (rewriteBatchedInserts) {
                "$jdbcUrl?currentSchema=$schemaName&reWriteBatchedInserts=true"
            } else {
                "$jdbcUrl?currentSchema=$schemaName"
            }
        }
        logger.info("Using Postgres URL $jdbcUrl".emphasise())
        return factory.create("org.postgresql.Driver",jdbcUrl, user, password)
    }

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val port = System.getProperty(POSTGRES_PORT_PROPERTY)
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        val postgresDb = getDatabase()
        val host = getPropertyNonBlank(POSTGRES_HOST_PROPERTY, "localhost")
        var jdbcUrl = "jdbc:postgresql://$host:$port/$postgresDb"
        if (!schemaName.isNullOrBlank()) {
            jdbcUrl = "$jdbcUrl?currentSchema=$schemaName"
        }
        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }
}