package net.corda.cli.plugin.initialconfig

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
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
        names = ["-t", "--type"],
        description = ["Secrets service type. Valid values: \${COMPLETION-CANDIDATES}. Default: \${DEFAULT-VALUE}. " +
                "Specifying CORDA generates a Config snippet based on 'passphrase' and 'salt', which are used to generate " +
                "a root key for the built in secrets service. Specifying VAULT generates a Config without these properties" +
                "as they are never used if using the Vault secrets service."]
    )
    var type: SecretsServiceType = SecretsServiceType.CORDA

    override fun run() {
        val (wrappingPassphraseSecret, wrappingSaltSecret) = if (type == SecretsServiceType.CORDA) {
            checkParamPassed(passphrase)
                { "'passphrase' must be set for CORDA type secrets." }
            checkParamPassed(salt)
                { "'salt' must be set for CORDA type secrets." }

            createWrappingPassphraseAndSaltSecrets()
        } else {
            Pair(null, null)
        }

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
        val random = SecureRandom()

        val smartConfigFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseString(
                """
                ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=${passphrase}
                ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=${salt}
            """.trimIndent()
            ), listOf(EncryptionSecretsServiceFactory())
        )
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
        val wrappingPassphraseSecret =
            smartConfigFactory.makeSecret(wrappingPassphraseDefined, "corda-master-wrapping-key-passphrase").toSafeConfig().root()
        val wrappingSaltSecret =
            smartConfigFactory.makeSecret(wrappingSaltDefined, "corda-master-wrapping-key-salt").toSafeConfig().root()
        return Pair(wrappingPassphraseSecret, wrappingSaltSecret)
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