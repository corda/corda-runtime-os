package net.corda.cli.plugin.secretconfig

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.secret.SecretsCreateService

private const val VAULT_PATH = "vaultPath"
private const val VAULT_KEY = "vaultKey"

class VaultSecretConfigGenerator(private val vaultPath: String) : SecretsCreateService {
    override fun createValue(plainText: String, key: String): Config {
        val secretConfig = mapOf(
            SmartConfig.SECRET_KEY to mapOf(VAULT_PATH to vaultPath, VAULT_KEY to plainText),
        )
        return ConfigFactory.parseMap(secretConfig)
    }
}
