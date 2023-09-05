package net.corda.db.testkit

import com.typesafe.config.Config
import net.corda.db.core.CloseableDataSource
import net.corda.orm.EntityManagerConfiguration
import java.sql.Connection

interface DbUtilsHelper {

    fun isInMemory(): Boolean

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

    fun createDataSource(
        dbUser:String? = null,
        dbPassword: String? = null,
        schemaName: String? = null,
        createSchema: Boolean = false,
        rewriteBatchedInserts: Boolean = false
    ): CloseableDataSource

    fun createConfig(
        inMemoryDbName: String = "",
        dbUser: String? = null,
        dbPassword: String? = null,
        schemaName: String? = null
    ): Config

    fun getPropertyNonBlank(key: String, defaultValue: String): String {
        val value = System.getProperty(key)
        return if (value.isNullOrBlank()) {
            defaultValue
        } else {
            value
        }
    }

    fun Connection.createSchema(schemaName: String?) {
        requireNotNull(schemaName)
        this.use { conn ->
            conn.prepareStatement("CREATE SCHEMA IF NOT EXISTS $schemaName;").execute()
            conn.commit()
        }
    }
}