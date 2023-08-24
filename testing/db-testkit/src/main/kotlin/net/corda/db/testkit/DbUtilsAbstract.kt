package net.corda.db.testkit

import com.typesafe.config.Config
import net.corda.db.core.BaseDataSourceFactory
import net.corda.db.core.CloseableDataSource
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import net.corda.test.util.LoggingUtils.emphasise

import org.slf4j.Logger
import java.sql.Connection


abstract class DbUtilsAbstract {

    abstract val hostProperty : String
    abstract val portProperty : String
    abstract val dbName: String
    abstract val adminUser: String
    abstract val adminPassword: String

    abstract val isInMemory : Boolean

    abstract val host : String

    abstract val logger: Logger

    abstract var jdbcURL : String

    abstract fun getFactory(): BaseDataSourceFactory

    @Suppress("LongParameterList")
    fun getEntityManagerConfiguration(
        inMemoryDbName : String,
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        showSql: Boolean = true,
        rewriteBatchedInserts: Boolean = false
    ):EntityManagerConfiguration {
        val port = System.getProperty(portProperty)
        return if (!port.isNullOrBlank()) {
            val ds =
                createDataSource(dbUser, dbPassword, schemaName, createSchema, rewriteBatchedInserts)
            DbEntityManagerConfiguration(ds, showSql, true, DdlManage.NONE)
        } else {
            logger.info("Using in-memory (HSQL) DB".emphasise())
            TestInMemoryEntityManagerConfiguration(inMemoryDbName, showSql).also {
                if (createSchema) {
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
        val factory = getFactory()
        val user = dbUser ?: adminUser
        val password = dbPassword ?: adminPassword
        if (!schemaName.isNullOrBlank()) {
            if (createSchema) {
                logger.info("Creating schema: $schemaName".emphasise())
                factory.create(jdbcURL, user, password, maximumPoolSize = 1).connection.createSchema(schemaName)
            }
            val jdbcURLCopy = if (rewriteBatchedInserts) {
                "$jdbcURL?currentSchema=$schemaName&reWriteBatchedInserts=true"
            } else {
                "$jdbcURL?currentSchema=$schemaName"
            }
            logger.info("Using Postgres URL $jdbcURL".emphasise())
            return factory.create(jdbcURLCopy,user,password)
        }
        logger.info("Using Postgres URL $jdbcURL".emphasise())
        return factory.create(jdbcURL, user, password)
    }


    abstract fun createConfig(
        inMemoryDbName: String,
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config
}

private fun Connection.createSchema(schemaName: String?) {
    requireNotNull(schemaName)
    this.use { conn ->
        conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
        conn.commit()
    }
}