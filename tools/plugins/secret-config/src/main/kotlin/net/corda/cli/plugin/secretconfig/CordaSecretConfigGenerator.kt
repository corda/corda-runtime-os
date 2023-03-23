package net.corda.cli.plugin.secretconfig

import com.typesafe.config.Config
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl

class CordaSecretConfigGenerator(
    passphrase: String, salt: String
) : SecretConfigGenerator {
    private val encryptionService = EncryptionSecretsServiceImpl(passphrase, salt)

    override fun generate(value: String): Config = encryptionService.createValue(value, "unused")
}
