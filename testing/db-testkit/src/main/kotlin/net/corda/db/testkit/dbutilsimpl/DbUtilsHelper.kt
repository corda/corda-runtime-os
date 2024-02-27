package net.corda.db.testkit.dbutilsimpl

import com.typesafe.config.Config
import net.corda.db.core.CloseableDataSource
import net.corda.orm.EntityManagerConfiguration

/**
 * An interface defining common database utility methods for managing database connections and configurations.
 */
interface DbUtilsHelper {
    fun getDatabase(): String

    fun getAdminUser(): String

    fun getAdminPassword(): String

    @Suppress("LongParameterList")
    fun getEntityManagerConfiguration(
        inMemoryDbName : String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        showSql: Boolean = true,
        rewriteBatchedInserts: Boolean = false
    ): EntityManagerConfiguration

    @Suppress("LongParameterList")
    fun createDataSource(
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        rewriteBatchedInserts: Boolean = false,
        maximumPoolSize: Int = 5
    ): CloseableDataSource

    fun createConfig(
        inMemoryDbName: String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config
 }