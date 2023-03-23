package net.corda.cli.plugin.secretconfig

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig

private const val VAULT_PATH = "vaultPath"
private const val VAULT_KEY = "vaultKey"

class VaultSecretConfigGenerator(private val vaultPath: String) : SecretConfigGenerator {
    override fun generate(value: String): Config {
        val secretConfig = mapOf(
            SmartConfig.SECRET_KEY to mapOf(VAULT_PATH to vaultPath, VAULT_KEY to value),
        )
        return ConfigFactory.parseMap(secretConfig)
    }
}
