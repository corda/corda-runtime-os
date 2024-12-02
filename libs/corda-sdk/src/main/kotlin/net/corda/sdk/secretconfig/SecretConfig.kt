package net.corda.sdk.secretconfig

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.helper.VaultSecretConfigGenerator
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.SecretEncryptionUtil

object SecretConfig {

    private const val SECRET_KEY = "unused"

    fun createCordaSecret(value: String, passphrase: String, salt: String): String {
        val secretConfigGenerator = EncryptionSecretsServiceImpl(passphrase, salt)
        return secretConfigGenerator.createValue(value, SECRET_KEY).asString()
    }

    fun createVaultSecret(value: String, vaultPath: String): String {
        val secretConfigGenerator = VaultSecretConfigGenerator(vaultPath)
        return secretConfigGenerator.createValue(value, SECRET_KEY).asString()
    }

    fun decrypt(
        value: String,
        passphrase: String,
        salt: String
    ): String {
        val secretEncryptionUtil = SecretEncryptionUtil()
        return secretEncryptionUtil.decrypt(value, salt, passphrase)
    }

    private fun Config.asString(): String {
        return this.root().render(ConfigRenderOptions.concise())
    }
}
