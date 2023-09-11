package net.corda.db.testkit

import com.typesafe.config.Config
import net.corda.db.core.CloseableDataSource
import net.corda.db.testkit.dbutilsimpl.DbUtilsHelper
import net.corda.db.testkit.dbutilsimpl.HSQLHelper
import net.corda.db.testkit.dbutilsimpl.PostgresHelper
import net.corda.db.testkit.dbutilsimpl.SQLServerHelper
import net.corda.orm.EntityManagerConfiguration

object DbUtils {

    private val isPostgres = !System.getProperty("postgresPort").isNullOrBlank()
    private val isMSSQL = !System.getProperty("mssqlPort").isNullOrBlank()

    private val utilsHelper: DbUtilsHelper = when {
        isMSSQL && isPostgres -> throw IllegalArgumentException(
            "Both postgres and SQL server are set, please only set properties for one DB type"
        )
        isMSSQL -> SQLServerHelper()
        isPostgres -> PostgresHelper()
        else -> HSQLHelper()
    }

    val isInMemory = utilsHelper.isInMemory()

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

    /**
     * Creates Postgres [CloseableDataSource]
     *
     * @param dbUser DB user. If value is not provided, value of the system property "postgresUser" is used.
     *               If system property is not set then value "postgress" is used.
     * @param dbPassword DB password. If value is not provided, value of the system property "postgresPassword" is used.
     *                   If system property is not set then value "password" is used
     */
    @Suppress("LongParameterList")
    fun createDataSource(
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        rewriteBatchedInserts: Boolean = false,
        maximumPoolSize: Int = 5
    ): CloseableDataSource = utilsHelper.createDataSource(
        dbUser, dbPassword, schemaName, createSchema, rewriteBatchedInserts, maximumPoolSize
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
