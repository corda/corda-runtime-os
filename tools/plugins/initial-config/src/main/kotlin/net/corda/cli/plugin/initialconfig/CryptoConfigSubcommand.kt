package net.corda.cli.plugin.initialconfig

import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.helper.VaultSecretConfigGenerator
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import picocli.CommandLine
import picocli.CommandLine.ParameterException
import java.io.File
import java.io.FileWriter
import java.security.InvalidParameterException
import java.security.SecureRandom
import java.time.Instant
import java.util.*

@CommandLine.Command(
    name = "create-crypto-config",
    description = [
        "Creates and saves to the database the initial crypto configuration." +
                "The operation must be done after the cluster database is initialised" +
                "but before the cluster is started."
    ],
    mixinStandardHelpOptions = true
)
class CryptoConfigSubcommand : Runnable {
    enum class SecretsServiceType {
        CORDA, VAULT
    }

    @CommandLine.Spec
    var spec: CommandLine.Model.CommandSpec? = null // injected by picocli


    @CommandLine.Option(
        names = ["-s", "--salt"],
        description = ["Salt for the encrypting secrets service. Mandatory for CORDA type secrets service."]
    )
    var salt: String? = null

    @CommandLine.Option(
        names = ["-p", "--passphrase"],
        description = ["Passphrase for the encrypting secrets service. Mandatory for CORDA type secrets service."]
    )
    var passphrase: String? = null

    @CommandLine.Option(
        names = ["-ws", "--wrapping-salt"],
        description = ["Salt for the SOFT HSM root wrapping key."]
    )
    var softHsmRootSalt: String? = null

    @CommandLine.Option(
        names = ["-wp", "--wrapping-passphrase"],
        description = ["Passphrase for the SOFT HSM root wrapping key."]
    )
    var softHsmRootPassphrase: String? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["location to write the sql output to."]
    )
    var location: String? = null

    @CommandLine.Option(
        names = ["-v", "--vault-path"],
        description = ["Vault path of the secrets located in HashiCorp Vault. Used only by VAULT type secrets service."]
    )
    var vaultPath: String? = null

    @CommandLine.Option(
        names = ["-ks", "--key-salt"],
        description = ["Vault key for the wrapping key salt. Used only by VAULT type secrets service."],
        defaultValue = "corda-master-wrapping-key-passphrase",
    )
    var vaultWrappingKeySalt: String = "corda-master-wrapping-key-passphrase"

    @CommandLine.Option(
        names = ["-kp", "--key-passphrase"],
        description = ["Vault key for the wrapping key service passphrase. Used only by VAULT type secrets service."],
        defaultValue = "corda-master-wrapping-key-passphrase",
    )
    var vaultWrappingKeyPassphrase: String = "corda-master-wrapping-key-passphrase"

    @CommandLine.Option(
        names = ["-t", "--type"],
        description = ["Secrets service type. Valid values: \${COMPLETION-CANDIDATES}. Default: \${DEFAULT-VALUE}. " +
                "Specifying CORDA generates a Config snippet with a wrapping passphrase and salt encoded with the Corda " +
                "built in encryption secrets service. The passphrase and salt for the Corda encryption secrets service must " +
                "be passed as parameters for this type. Specifying VAULT generates a Config where the wrapping passphrase " +
                "and salt are to be extracted from Vault. The Vault path and keys must be passed as parameters for this type."]
    )
    var type: SecretsServiceType = SecretsServiceType.CORDA

    override fun run() {
        val (wrappingPassphraseSecret, wrappingSaltSecret) = createWrappingPassphraseAndSaltSecrets()

        val config = createDefaultCryptoConfig(wrappingPassphraseSecret.root(), wrappingSaltSecret.root()).root()
            .render(ConfigRenderOptions.concise())

        val entity = ConfigEntity(
            section = CRYPTO_CONFIG,
            config = config,
            schemaVersionMajor = 1,
            schemaVersionMinor = 0,
            updateTimestamp = Instant.now(),
            updateActor = "init",
            isDeleted = false
        ).apply {
            version = 0
        }

        val output = entity.toInsertStatement()

        if (location == null) {
            println(output)
        } else {
            FileWriter(File("${location!!.removeSuffix("/")}/crypto-config.sql")).run {
                write(output)
                flush()
                close()
            }
        }
    }

    private fun createWrappingPassphraseAndSaltSecrets(): Pair<Config, Config> = when (type) {
        SecretsServiceType.CORDA -> {
            val ess = EncryptionSecretsServiceImpl(
                checkParamPassed(passphrase)
                { "'passphrase' must be set for CORDA type secrets." },
                checkParamPassed(salt)
                { "'salt' must be set for CORDA type secrets." }
            )
            val (passphraseSecretValue, saltSecretValue) = generateSecretValuesForType()
            Pair(
                ess.createValue(passphraseSecretValue, "unused"),
                ess.createValue(saltSecretValue, "unused")
            )
        }
        SecretsServiceType.VAULT -> {
            val vss = VaultSecretConfigGenerator(checkParamPassed(vaultPath)
            { "'vaultPath' must be set for VAULT type secrets." })
            Pair(
                vss.createValue("unused", vaultWrappingKeyPassphrase),
                vss.createValue("unused", vaultWrappingKeySalt)
            )
        }
    }

    private fun generateSecretValuesForType(): Pair<String, String> {
        return if (type == SecretsServiceType.VAULT) {
            Pair(
                checkParamPassed(vaultWrappingKeyPassphrase) { "'vaultWrappingKeyPassphrase' must be set for VAULT type secrets." },
                checkParamPassed(vaultWrappingKeySalt) { "'vaultWappiingKeySalt' must be set for VAULT type secrets." }
            )
        } else { // type == SecretsServiceType.CORDA
            val random = SecureRandom()

            // Use the salt and passphrase passed to the plugin by the user, or generate random ones if not supplied
            val wrappingPassphraseDefined = (if (softHsmRootPassphrase.isNullOrBlank()) {
                random.randomString()
            } else {
                softHsmRootPassphrase!!
            })
            val wrappingSaltDefined = (if (softHsmRootSalt.isNullOrBlank()) {
                random.randomString()
            } else {
                softHsmRootSalt!!
            })
            Pair(wrappingPassphraseDefined, wrappingSaltDefined)
        }
    }

    private fun SecureRandom.randomString(length: Int = 32): String = ByteArray(length).let {
        this.nextBytes(it)
        Base64.getEncoder().encodeToString(it)
    }

    private inline fun checkParamPassed(value: String?, lazyMessage: () -> String) = if (value.isNullOrBlank()) {
        val specNotNull = spec ?: throw InvalidParameterException(lazyMessage())
        throw ParameterException(specNotNull.commandLine(), lazyMessage())
    } else {
        value
    }
}