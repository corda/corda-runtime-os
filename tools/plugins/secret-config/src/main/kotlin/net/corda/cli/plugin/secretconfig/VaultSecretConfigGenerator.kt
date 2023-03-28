package net.corda.cli.plugin.secretconfig

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.schema.configuration.ConfigKeys

class VaultSecretConfigGenerator(private val vaultPath: String) : SecretsCreateService {
    override fun createValue(plainText: String, key: String): Config {
        val secretConfig = mapOf(
            ConfigKeys.SECRET_KEY to mapOf(
                ConfigKeys.SECRET_KEY_VAULT_PATH to vaultPath,
                ConfigKeys.SECRET_KEY_VAULT_KEY to plainText
            ),
        )
        return ConfigFactory.parseMap(secretConfig)
    }
}
