package net.corda.db.testkit

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.orm.EntityManagerConfiguration

/**
 * Database information, such as name, schema, [SmartConfig] and [EntityManagerConfiguration]
 */
class TestDbInfo(
    val name: String,
    val schemaName: String? = null,
    showSql: Boolean = true,
    rewriteBatchedInserts: Boolean = false
) {
    companion object {
        private val configFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseString(
                """
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=key
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            ),
            listOf(EncryptionSecretsServiceFactory())
        )

        fun createConfig() = TestDbInfo(name = CordaDb.CordaCluster.persistenceUnitName)
    }

    val config: SmartConfig = run {
        var config = configFactory.create(
            DbUtils.createConfig(
                inMemoryDbName = name,
                schemaName = schemaName
            )
        )

        if (!DbUtils.isInMemory) {
            val jdbcDriverConfig =
                ConfigFactory.empty()
                    .withValue("database.jdbc.driver", ConfigValueFactory.fromAnyRef("org.postgresql.Driver"))
            config = config.withFallback(
                jdbcDriverConfig
            )
        }
        config
    }

    val emConfig: EntityManagerConfiguration = DbUtils.getEntityManagerConfiguration(
        inMemoryDbName = name,
        schemaName = schemaName,
        showSql = showSql,
        rewriteBatchedInserts = rewriteBatchedInserts
    )
}
