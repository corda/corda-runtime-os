package net.corda.application.dbsetup

import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Instant
import java.util.*

class ConfigEntityFactory(
    private val smartConfigFactory: SmartConfigFactory
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun createConfiguration(
        connectionName: String,
        username: String,
        password: String,
        jdbcUrl: String,
        privilege: DbPrivilege
    ): DbConnectionConfig {
        log.info("Initialise configuration for $connectionName ($jdbcUrl).")

        return DbConnectionConfig(
            id = UUID.randomUUID(),
            name = connectionName,
            privilege = privilege,
            updateTimestamp = Instant.now(),
            updateActor = "Setup Script",
            description = "Initial configuration - autogenerated by setup script",
            config = createDbConfig(jdbcUrl, username, password, connectionName)
        ).also { it.version = 0 }
    }

    fun createCryptoConfig(): ConfigEntity {
        val random = SecureRandom()
        val config = createDefaultCryptoConfig(
            listOf(
                smartConfigFactory.makeSecret(random.randomString(), "corda-master-wrapping-key-passphrase").root(),
                smartConfigFactory.makeSecret(random.randomString(), "corda-master-wrapping-key-2-passphrase").root()
            ),
            listOf(
                smartConfigFactory.makeSecret(random.randomString(), "corda-master-wrapping-key-salt").root(),
                smartConfigFactory.makeSecret(random.randomString(), "corda-master-wrapping-key-salt-2").root(),
            )
        ).root().render(ConfigRenderOptions.concise())

        return ConfigEntity(
            section = ConfigKeys.CRYPTO_CONFIG,
            config = config,
            schemaVersionMajor = 1,
            schemaVersionMinor = 0,
            updateTimestamp = Instant.now(),
            updateActor = "init",
            isDeleted = false
        ).apply {
            version = 0
        }
    }

    private fun createDbConfig(
        jdbcUrl: String,
        username: String,
        password: String,
        connectionName: String
    ): String {
        return "{\"database\":{" +
                "\"jdbc\":" +
                "{\"url\":\"$jdbcUrl\"}," +
                "\"pass\":${createSecureConfig(password, "$connectionName-database-password")}," +
                "\"user\":\"$username\"}}"
    }

    private fun createSecureConfig(value: String, key: String): String {
        return smartConfigFactory.makeSecret(value, key).root().render(ConfigRenderOptions.concise())
    }

    private fun SecureRandom.randomString(length: Int = 32): String = ByteArray(length).let {
        this.nextBytes(it)
        Base64.getEncoder().encodeToString(it)
    }
}