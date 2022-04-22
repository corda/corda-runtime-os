package net.corda.processors.crypto.tests.infra

import com.typesafe.config.ConfigFactory
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.SmartConfigFactory

class TestDbInfo(
    val name: String,
    schemaName: String? = null
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
    }

    val config = configFactory.create(
        DbUtils.createConfig(
            inMemoryDbName = name,
            schemaName = schemaName
        )
    )

    val emConfig = DbUtils.getEntityManagerConfiguration(
        inMemoryDbName = name,
        schemaName = schemaName
    )
}