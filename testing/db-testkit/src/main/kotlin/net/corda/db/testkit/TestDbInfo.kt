package net.corda.db.testkit

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.orm.EntityManagerConfiguration
import net.corda.schema.configuration.ConfigKeys

/**
 * Database information, such as name, schema, [SmartConfig] and [EntityManagerConfiguration]
 */
class TestDbInfo(
    val name: String,
    val schemaName: String? = null
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

    // TODO This was temporarily changed to work with in-memory DB until CORE-5418 is fixed
    val config: SmartConfig = configFactory.create(
        ConfigFactory.empty()
            .withValue(ConfigKeys.JDBC_DRIVER, ConfigValueFactory.fromAnyRef("org.hsqldb.jdbc.JDBCDriver"))
            .withValue(ConfigKeys.JDBC_URL, ConfigValueFactory.fromAnyRef("jdbc:hsqldb:mem:$name"))
            .withValue(ConfigKeys.DB_USER, ConfigValueFactory.fromAnyRef("sa"))
            .withValue(ConfigKeys.DB_PASS, ConfigValueFactory.fromAnyRef(""))
    )

    // TODO This was temporarily changed to work with in-memory DB until CORE-5418 is fixed
    val emConfig: EntityManagerConfiguration = TestInMemoryEntityManagerConfiguration(name)
}