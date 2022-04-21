package net.corda.processors.crypto.tests.infra

import com.typesafe.config.ConfigFactory
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.SmartConfigFactory

class TestDbInfo(
    val name: String
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

    val config = configFactory.create(DbUtils.createConfig(name))

    val emConfig = DbUtils.getEntityManagerConfiguration(name)
}