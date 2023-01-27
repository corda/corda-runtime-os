package net.corda.cli.plugin.initialconfig

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigEntity
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
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
    @CommandLine.Option(
        names = ["-s", "--salt"],
        required = true,
        description = ["Salt for the encrypting secrets service."]
    )
    lateinit var salt: String

    @CommandLine.Option(
        names = ["-p", "--passphrase"],
        required = true,
        description = ["Passphrase for the encrypting secrets service."]
    )
    lateinit var passphrase: String

    @CommandLine.Option(
        names = ["-ws", "--wrapping-salt"],
        required = false,
        description = ["Salt for the SOFT HSM root wrapping key."]
    )
    var softHsmRootSalt: String? = null

    @CommandLine.Option(
        names = ["-wp", "--wrapping-passphrase"],
        required = false,
        description = ["Passphrase for the SOFT HSM root wrapping key."]
    )
    var softHsmRootPassphrase: String? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        required = false,
        description = ["location to write the sql output to."]
    )
    var location: String? = null

    override fun run() {
        val random = SecureRandom()
        val smartConfigFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseString("""
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=${passphrase}
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=${salt}
        """.trimIndent()
            ),
            listOf(EncryptionSecretsServiceFactory())
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
        val wrappingPassphraseSecret = smartConfigFactory.makeSecret(wrappingPassphraseDefined).toSafeConfig().root()
        val wrappingSaltSecret = smartConfigFactory.makeSecret(wrappingSaltDefined).toSafeConfig().root()
        val config = createDefaultCryptoConfig(wrappingSaltSecret, wrappingPassphraseSecret).root().render(ConfigRenderOptions.concise())

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

    private fun SecureRandom.randomString(length: Int = 32): String = ByteArray(length).let {
        this.nextBytes(it)
        Base64.getEncoder().encodeToString(it)
    }
}