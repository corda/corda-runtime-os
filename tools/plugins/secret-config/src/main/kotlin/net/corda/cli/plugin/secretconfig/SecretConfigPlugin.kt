package net.corda.cli.plugin.secretconfig

import com.typesafe.config.ConfigRenderOptions
import net.corda.cli.api.CordaCliPlugin
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.SecretEncryptionUtil
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.ParameterException
import picocli.CommandLine.Parameters

class SecretConfigPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    @Extension
    @Command(
        name = "secret-config",
        description = ["Handle secret config values given the value, a passphrase and a salt"]
    )
    class PluginEntryPoint : CordaCliPlugin {
        @Parameters(index = "0", description = ["The value to secure for configuration"])
        lateinit var value: String

        @Option(
            names = ["-p", "--passphrase"],
            description = ["Passphrase for the encrypting secrets service"]
        )
        var passphrase: String? = null

        @Option(
            names = ["-s", "--salt"],
            description = ["Salt for the encrypting secrets service"]
        )
        var salt: String? = null

        @CommandLine.Spec
        lateinit var spec: CommandLine.Model.CommandSpec

        @Suppress("Unused")
        @Command(
            name = "create",
            description = ["Create a secret config value given a salt and a passphrase"]
        )
        fun create() {
            if (salt.isNullOrBlank()) {
                throw ParameterException(spec.commandLine(), "A salt must be provided")
            }
            if (passphrase.isNullOrBlank()) {
                throw ParameterException(spec.commandLine(), "A passphrase must be provided")
            }
            val encryptionService = EncryptionSecretsServiceImpl(passphrase!!, salt!!)

            val configSection = encryptionService.createValue(value, "test")

            println(configSection.root().render(ConfigRenderOptions.concise()))
        }

        @Suppress("Unused")
        @Command(
            name = "decrypt",
            description = ["Decrypt a secret value given salt and passphrase (takes the actual value, not the config)"]
        )
        fun decrypt() {
            if (salt.isNullOrBlank()) {
                throw ParameterException(spec.commandLine(), "A salt must be provided")
            }
            if (passphrase.isNullOrBlank()) {
                throw ParameterException(spec.commandLine(), "A passphrase must be provided")
            }
            val encryptionUtil = SecretEncryptionUtil()

            println(encryptionUtil.decrypt(value, salt!!, passphrase!!))
        }
    }
}
