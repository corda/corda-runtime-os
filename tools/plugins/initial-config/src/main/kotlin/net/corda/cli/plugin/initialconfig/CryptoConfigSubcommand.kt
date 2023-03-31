package net.corda.cli.plugin.initialconfig

import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.helper.VaultSecretConfigGenerator
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.lang.IllegalArgumentException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@CommandLine.Command(
    name = "create-crypto-config",
    description = [
        "Creates and saves to the database the initial crypto configuration." +
                "The operation must be done after the cluster database is initialised" +
                "but before the cluster is started."
    ]
)
class CryptoConfigSubcommand : Runnable {
    enum class SecretsServiceType {
        CORDA, VAULT
    }

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
        description = ["Salt for the SOFT HSM root wrapping key. Used only by CORDA type secrets service."]
    )
    var softHsmRootSalt: String? = null

    @CommandLine.Option(
        names = ["-wp", "--wrapping-passphrase"],
        description = ["Passphrase for the SOFT HSM root wrapping key. Used only by CORDA type secrets service."]
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
        description = ["Vault key for the secrets service salt. Used only by VAULT type secrets service."]
    )
    var vaultKeySalt: String? = null

    @CommandLine.Option(
        names = ["-kp", "--key-passphrase"],
        description = ["Vault key for the secrets service passphrase. Used only by VAULT type secrets service."]
    )
    var vaultKeyPassphrase: String? = null

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

        val config = createDefaultCryptoConfig(wrappingPassphraseSecret, wrappingSaltSecret).root().render(ConfigRenderOptions.concise())

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

    private fun createWrappingPassphraseAndSaltSecrets(): Pair<ConfigObject, ConfigObject> {
        val secretsService: SecretsCreateService = when (type) {
            SecretsServiceType.CORDA -> EncryptionSecretsServiceImpl(
                checkParamPassed(passphrase)
                { "'passphrase' must be set for CORDA type secrets." },
                checkParamPassed(salt)
                { "'salt' must be set for CORDA type secrets." })

            SecretsServiceType.VAULT -> VaultSecretConfigGenerator(
                checkParamPassed(vaultPath)
                { "'vaultPath' must be set for VAULT type secrets." })
        }

        val (passphraseSecretValue, saltSecretValue) = generateSecretValuesForType()

        val wrappingPassphraseSecret =
            secretsService.createValue(passphraseSecretValue, "corda-master-wrapping-key-passphrase").root()
        val wrappingSaltSecret =
            secretsService.createValue(saltSecretValue, "corda-master-wrapping-key-salt").root()
        return Pair(wrappingPassphraseSecret, wrappingSaltSecret)
    }

    private fun generateSecretValuesForType(): Pair<String, String> {
        return if (type == SecretsServiceType.VAULT) {
            Pair(
                checkParamPassed(vaultKeyPassphrase) { "'vaultKeyPassphrase' must be set for VAULT type secrets." },
                checkParamPassed(vaultKeySalt) { "'vaultKeySalt' must be set for VAULT type secrets." }
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
        throw IllegalArgumentException(lazyMessage())
    } else {
        value
    }
}