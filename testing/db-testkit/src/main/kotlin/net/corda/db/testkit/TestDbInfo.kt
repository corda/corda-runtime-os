package net.corda.db.testkit

import com.typesafe.config.ConfigFactory
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.orm.EntityManagerConfiguration

/**
 * Database information, such as name, schema, [SmartConfig] and [EntityManagerConfiguration]
 */
class TestDbInfo(
    val name: String,
    val schemaName: String? = null,
    showSql: Boolean = true
) {
    companion object {
        private val configFactory = SmartConfigFactory.create(
            ConfigFactory.parseString(
                """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            )
        )

        fun createConfig() = TestDbInfo(name = CordaDb.CordaCluster.persistenceUnitName)
    }

    val config: SmartConfig = configFactory.create(
        DbUtils.createConfig(
            inMemoryDbName = name,
            schemaName = schemaName
        )
    )

    val emConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration(
        inMemoryDbName = name,
        schemaName = schemaName,
        showSql = showSql
    )
}
