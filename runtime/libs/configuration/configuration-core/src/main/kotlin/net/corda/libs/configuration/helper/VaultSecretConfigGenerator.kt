package net.corda.libs.configuration.helper

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.schema.configuration.ConfigKeys

class VaultSecretConfigGenerator(private val vaultPath: String) : SecretsCreateService {
    override fun createValue(@Suppress("UNUSED_PARAMETER") plainText: String, key: String): Config {
        val secretConfig = mapOf(
            ConfigKeys.SECRET_KEY to mapOf(
                ConfigKeys.SECRET_KEY_VAULT_PATH to vaultPath,
                ConfigKeys.SECRET_KEY_VAULT_KEY to key
            ),
        )
        return ConfigFactory.parseMap(secretConfig)
    }
}
