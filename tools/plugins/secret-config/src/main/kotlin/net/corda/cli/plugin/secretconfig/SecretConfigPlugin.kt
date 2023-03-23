package net.corda.cli.plugin.secretconfig

import com.typesafe.config.ConfigRenderOptions
import net.corda.cli.api.CordaCliPlugin
import net.corda.libs.configuration.secret.SecretEncryptionUtil
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

class SecretConfigPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    @Extension
    @Command(
        name = "secret-config",
        description = ["Generate secret Config values which can be inserted into your Corda Config, removing the need to " +
                "put sensitive values in plain text. The output will depend on the type of secrets service being used. " +
                "See 'type' for more information."]
    )
    class PluginEntryPoint : CordaCliPlugin {
        enum class SecretsServiceType {
            CORDA, VAULT
        }

        @Parameters(
            index = "0",
            description = ["The value to secure for configuration for CORDA type secrets service. The key of the secret " +
                    "for VAULT type secrets."],
        )
        lateinit var value: String

        @Option(
            names = ["-p", "--passphrase"],
            description = ["Passphrase for the encrypting secrets service, used only by CORDA type secrets service."]
        )
        var passphrase: String? = null

        @Option(
            names = ["-s", "--salt"],
            description = ["Salt for the encrypting secrets service, used only by CORDA type secrets service."]
        )
        var salt: String? = null

        @Option(
            names = ["-v", "--vault-path"],
            description = ["Vault path of the secret located in HashiCorp Vault. Used only by VAULT type secrets service."]
        )
        var vaultPath: String? = null

        @Option(
            names = ["-t", "--type"],
            description = ["Secrets service type. Valid values: \${COMPLETION-CANDIDATES}. Default: \${DEFAULT-VALUE}. " +
                    "CORDA generates a Config snippet based on 'passphrase' and 'salt' which are the same passphrase and " +
                    "salt you would pass in at Corda bootstrapping to use built in Corda decryption to hide secrets in the Config." +
                    "VAULT generates a configuration compatible with the HashiCorp Vault Corda addon, available to Corda Enterprise. " +
                    "For VAULT Config generation you must supply the 'vault-path' parameter as well as the key of the secret."]
        )
        var type: SecretsServiceType = SecretsServiceType.CORDA

        @CommandLine.Spec
        lateinit var spec: CommandLine.Model.CommandSpec

        @Suppress("Unused")
        @Command(
            name = "create", description = ["Create a secret config value given a salt and a passphrase"]
        )
        fun create() {
            try {
                val secretConfigGenerator: SecretConfigGenerator = when (type) {
                    SecretsServiceType.CORDA -> CordaSecretConfigGenerator(
                        checkParamPassed(passphrase)
                            { "'passphrase' must be set for CORDA type secrets." },
                        checkParamPassed(salt)
                            { "'salt' must be set for CORDA type secrets." }
                    )

                    SecretsServiceType.VAULT -> VaultSecretConfigGenerator(
                        checkParamPassed(vaultPath) {
                            "'vaultPath' must be set for VAULT type secrets." }
                    )
                }

                val configSection = secretConfigGenerator.generate(value)
                println(configSection.root().render(ConfigRenderOptions.concise()))

            } catch (_: ParamValidationException) {
                // Logged at point of check
            }
        }

        @Suppress("Unused")
        @Command(
            name = "decrypt",
            description = ["Decrypt a CORDA secret value given salt and passphrase (takes the actual value, not the config). " +
                "Does not apply to VAULT type secrets which have no encrypted Config content."]
        )
        fun decrypt() {
            if (type != SecretsServiceType.CORDA) {
                println("'decrypt' command can only be run on secrets created by CORDA secrets service.")
                return
            }

            try {
                val encryptionUtil = SecretEncryptionUtil()

                println(encryptionUtil.decrypt(value,
                    checkParamPassed(salt)
                        { "'salt' must be set for CORDA type secrets." },
                    checkParamPassed(passphrase)
                        { "'passphrase' must be set for CORDA type secrets." }
                    )
                )
            } catch (_: ParamValidationException) {
                // Logged at point of check
            }
        }

        class ParamValidationException : Exception()

        private inline fun checkParamPassed(value: String?, lazyMessage: () -> Any): String {
            if (value.isNullOrBlank()) {
                val message = lazyMessage()
                println(message.toString())
                throw ParamValidationException()
            } else {
                return value
            }
        }
    }
}
