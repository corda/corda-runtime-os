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

        if (!schemaName.isNullOrBlank()) {
            val adminUser = getAdminUser()
            val adminPassword = getAdminPassword()
            val adminDataSource = net.corda.db.core.createUnpooledDataSource(
                driverClass,
                jdbcUrl,
                adminUser,
                adminPassword,
                maximumPoolSize = maximumPoolSize
            )

            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                adminDataSource.connection.use { conn ->
                    val sql = """
                        CREATE SCHEMA IF NOT EXISTS $schemaName;
                    """.trimIndent()
                    conn.prepareStatement(sql).execute()
                    conn.commit()
                }
            }

            if (dbUser != null) {
                adminDataSource.connection.use { conn ->
                    val createUserSql = """
                        DO 
                        ${'$'}${'$'} 
                        BEGIN 
                            IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$dbUser') THEN 
                                RAISE NOTICE 'Role "$dbUser" already exists'; 
                            ELSE 
                                CREATE USER "$dbUser" WITH PASSWORD '$password'; 
                            END IF; 
                        END 
                        ${'$'}${'$'};
                        GRANT ALL ON SCHEMA $schemaName TO "$dbUser";
                        ALTER ROLE "$dbUser" SET search_path TO $schemaName;
                            """.trimIndent()
                    conn.prepareStatement(createUserSql).execute()
                    conn.commit()
                }
            } else {
                adminDataSource.connection.use { conn ->
                    conn.prepareStatement("ALTER ROLE $adminUser SET search_path TO public, $schemaName;").execute()
                    conn.commit()
                }
            }
        }

        val jdbcUrlCopy = if (rewriteBatchedInserts) {
            "$jdbcUrl?reWriteBatchedInserts=true"
        } else {
            jdbcUrl
        }
        logger.info("Using URL $jdbcUrlCopy".emphasise())

        return net.corda.db.core.createUnpooledDataSource(
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
        return ConfigFactory.empty()
            .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcUrl))
            .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
            .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
    }
}