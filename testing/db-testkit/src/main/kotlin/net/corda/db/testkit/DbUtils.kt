package net.corda.db.testkit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.core.CloseableDataSource
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.test.util.LoggingUtils.emphasise
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import net.corda.db.core.PostgresDataSourceFactory
import net.corda.db.core.SQLDataSourceFactory
import net.corda.schema.configuration.DatabaseConfig


object DbUtils {

    private const val POSTGRES_HOST_PROPERTY = "postgresHost"
    private const val POSTGRES_PORT_PROPERTY = "postgresPort"
    private const val MSSQL_HOST_PROPERTY = "mssqlHost"
    private const val MSSQL_PORT_PROPERTY = "mssqlPort"

    val isPostgres = System.getProperty(POSTGRES_PORT_PROPERTY).isNullOrBlank()
    val isMSSQL = System.getProperty(MSSQL_PORT_PROPERTY).isNullOrBlank()

    val isInMemory = System.getProperty(POSTGRES_PORT_PROPERTY).isNullOrBlank() && System.getProperty(
        MSSQL_PORT_PROPERTY).isNullOrBlank()

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getDatabase() =
        if (!isPostgres){
            getPropertyNonBlank("postgresDb", "postgres")
        }
        else if (!isMSSQL){
            getPropertyNonBlank("sqlDb", "sql")
        }
        else {
            ""
        }

    fun getAdminUser() =
        if(!isPostgres){
            getPropertyNonBlank("postgresUser","postgres")
        }
        else if(!isMSSQL){
            getPropertyNonBlank("mssqlUser", "sa")
        }
        else{
            "sa"
        }

    fun getAdminPassword() =
        if(!isPostgres){
            getPropertyNonBlank("postgresPassword", "password")
        }
        else if(!isMSSQL){
            getPropertyNonBlank("mssqlPassword", "password")
        }
        else{
            ""
        }
    @Suppress("LongParameterList")
    fun getEntityManagerConfiguration(
        inMemoryDbName : String,
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        showSql: Boolean = true,
        rewriteBatchedInserts: Boolean = false
    ):EntityManagerConfiguration{
        println(System.getProperty(MSSQL_PORT_PROPERTY))
        return if(!isPostgres || !isMSSQL) {
            val ds =
                createDataSource(dbUser, dbPassword, schemaName, createSchema, rewriteBatchedInserts)
            DbEntityManagerConfiguration(ds, showSql, true, DdlManage.NONE)
        } else {
            logger.info("Using in-memory (HSQL) DB".emphasise())
            TestInMemoryEntityManagerConfiguration(inMemoryDbName,showSql).also{
                if(createSchema){
                    it.dataSource.connection.createSchema(schemaName)
                }
            }
        }
    }


    fun createDataSource(
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        rewriteBatchedInserts: Boolean = false
    ): CloseableDataSource {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        if (!isPostgres){
            val port = System.getProperty(POSTGRES_PORT_PROPERTY)
            val host = getPropertyNonBlank(POSTGRES_HOST_PROPERTY,"localhost")
            val factory = PostgresDataSourceFactory()
            val db = getDatabase()
            var jdbcurl = "jdbc:postgresql://$host:$port/$db"
            if (!schemaName.isNullOrBlank()) {
                if (createSchema) {
                    logger.info("Creating schema: $schemaName".emphasise())
                    factory.create(jdbcurl, user, password, maximumPoolSize = 1).connection.createSchema(schemaName)
                }
                jdbcurl= if (rewriteBatchedInserts) {
                    "$jdbcurl?currentSchema=$schemaName&reWriteBatchedInserts=true"
                } else {
                    "$jdbcurl?currentSchema=$schemaName"
                }
                logger.info("Using Postgres URL $jdbcurl".emphasise())
                return factory.create(jdbcurl,user,password)
            }
            logger.info("Using Postgres URL $jdbcurl".emphasise())
            return factory.create(jdbcurl, user, password)
        }
        else{
            val port = System.getProperty(MSSQL_PORT_PROPERTY)
            val host = getPropertyNonBlank(MSSQL_HOST_PROPERTY,"localhost")
            val factory = SQLDataSourceFactory()
            var jdbcurl = "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;"
            if(!schemaName.isNullOrBlank()){
                if(createSchema){
                    logger.info("Creating schema: $schemaName".emphasise())
                    factory.create(jdbcurl, user, password, maximumPoolSize = 1).connection.createSchema(schemaName)
                }
                jdbcurl = if (rewriteBatchedInserts) {
                    "$jdbcurl?currentSchema=$schemaName&reWriteBatchedInserts=true"
                } else {
                    "$jdbcurl?currentSchema=$schemaName"
                }
                logger.info("Using SQL server URL $jdbcurl".emphasise())
                return factory.create(jdbcurl,user,password)
            }
            logger.info("Using SQL server URL $jdbcurl".emphasise())
            return factory.create(jdbcurl, user, password)
            }
    }

    fun createConfig(
        inMemoryDbName: String,
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config {
        val user = dbUser ?: getAdminUser()
        val password = dbPassword ?: getAdminPassword()
        if(!isPostgres){
            val port = System.getProperty(POSTGRES_PORT_PROPERTY)
            val host = getPropertyNonBlank(POSTGRES_HOST_PROPERTY,"localhost")
            val db = getDatabase()
            var jdbcurl = "jdbc:postgresql://$host:$port/$db"
            if (!schemaName.isNullOrBlank()) {
                jdbcurl = "$jdbcurl?currentSchema=$schemaName"
            }
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcurl))
                .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        }
        else if(!isMSSQL){
            val port = System.getProperty(MSSQL_PORT_PROPERTY)
            val host = getPropertyNonBlank(MSSQL_HOST_PROPERTY,"localhost")
            var jdbcurl = "jdbc:sqlserver://$host:$port;encrypt=true;trustServerCertificate=true;"
            if(!schemaName.isNullOrBlank()){
               jdbcurl = "$jdbcurl?currentSchema=$schemaName"
            }
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef(jdbcurl))
                .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        }
        else{
            return ConfigFactory.empty()
                .withValue(DatabaseConfig.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
                .withValue(DatabaseConfig.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$inMemoryDbName"))
                .withValue(DatabaseConfig.DB_USER, ConfigValueFactory.fromAnyRef(user))
                .withValue(DatabaseConfig.DB_PASS, ConfigValueFactory.fromAnyRef(password))
        }
    }
}

private fun getPropertyNonBlank(key: String, defaultValue: String): String {
    val value = System.getProperty(key)
    return if (value.isNullOrBlank()) {
        defaultValue
    } else {
        value
    }
}
private fun Connection.createSchema(schemaName: String?) {
    requireNotNull(schemaName)
    this.use { conn ->
        conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
        conn.commit()
    }
}