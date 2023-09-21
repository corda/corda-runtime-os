package net.corda.db.testkit

import com.typesafe.config.Config
import net.corda.db.core.CloseableDataSource
import net.corda.db.testkit.dbutilsimpl.DbUtilsHelper
import net.corda.db.testkit.dbutilsimpl.HSQLHelper
import net.corda.db.testkit.dbutilsimpl.PostgresHelper
import net.corda.db.testkit.dbutilsimpl.SQLServerHelper
import net.corda.orm.EntityManagerConfiguration

object DbUtils {

    private const val DBTYPE_PROPERTY = "databaseType"

    enum class DatabaseType {
        HSQL,
        POSTGRES,
        MSSQL
    }

    val databaseType = DatabaseType.valueOf(System.getProperty(DBTYPE_PROPERTY, "HSQL"))

    private val utilsHelper: DbUtilsHelper = when(databaseType) {
        DatabaseType.MSSQL -> SQLServerHelper()
        DatabaseType.POSTGRES -> PostgresHelper()
        DatabaseType.HSQL -> HSQLHelper()
    }

    val isInMemory = databaseType == DatabaseType.HSQL

    fun getDatabase() = utilsHelper.getDatabase()

    @Suppress("LongParameterList")
    fun getEntityManagerConfiguration(
        inMemoryDbName: String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        showSql: Boolean = true,
        rewriteBatchedInserts: Boolean = false
    ): EntityManagerConfiguration = utilsHelper.getEntityManagerConfiguration(
        inMemoryDbName, dbUser, dbPassword, schemaName, createSchema, showSql, rewriteBatchedInserts
    )

    fun createDataSource(
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        rewriteBatchedInserts: Boolean = false
    ): CloseableDataSource = utilsHelper.createDataSource(
        dbUser, dbPassword, schemaName, createSchema, rewriteBatchedInserts
    )

    fun createConfig(
        inMemoryDbName: String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config = utilsHelper.createConfig(
        inMemoryDbName, dbUser, dbPassword, schemaName
    )
}
