package net.corda.db.testkit.dbutilsimpl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.createDataSource
import net.corda.db.testkit.TestInMemoryEntityManagerConfiguration
import net.corda.orm.EntityManagerConfiguration
import net.corda.schema.configuration.DatabaseConfig
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HSQLHelper : DbUtilsHelper {
      override fun getDatabase(): String = ""

    override fun getAdminUser() = "sa"

    override fun getAdminPassword() = ""

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun getEntityManagerConfiguration(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?,
        createSchema: Boolean,
        showSql: Boolean,
        rewriteBatchedInserts: Boolean
    ): EntityManagerConfiguration {
        if (inMemoryDbName.isBlank()) {
            throw IllegalArgumentException("Please specify inMemoryDbName when using HSQL")
        }
        logger.info("Using in-memory (HSQL) DB".emphasise())
        return TestInMemoryEntityManagerConfiguration(inMemoryDbName, showSql).also {
            if (createSchema) {
                requireNotNull(schemaName)
                it.dataSource.connection.use { conn ->
                    conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
                    conn.commit()
                }

            }
        }
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
        return createDataSource("org.hsqldb.jdbc.JDBCDriver","", user, password)
    }

    override fun createConfig(
        inMemoryDbName: String,
        dbUser: String?,
        dbPassword: String?,
        schemaName: String?
    ): Config {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()

        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$inMemoryDbName"))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }
}