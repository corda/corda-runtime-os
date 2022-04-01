package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.TenantKeys
import net.corda.schema.TestSchema
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Callable

@Command(
    name = "add-key-pair",
    aliases = ["add-keys"],
    description = ["Add a key-pair"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
)
class AddKeyPair : Callable<Collection<Record<String, TenantKeys>>> {
    companion object {
        fun Config.toKeysRecord(topic: String = TestSchema.CRYPTO_KEYS_TOPIC): Record<String, TenantKeys> {
            val publishAlias = try {
                this.getString("publishAlias")
            } catch (_: Missing) {
                UUID.randomUUID().toString()
            }

            val keys = try {
                val keysFile = this.getString("keysFile")
                File(keysFile).readKeyPair()
                    ?: throw SetupException("Can not read key pair from $keysFile")
            } catch (e: Missing) {
                this.getString("keys").reader().readKeyPair()
                    ?: throw SetupException("Can not read key pair")
            }
            val privateKey = keys.private
            val publicKey = keys.public

            val keyAlgorithm = publicKey.toAlgorithm()

            val tenantId = this.getString("tenantId")

            return Record(
                topic, publishAlias,
                TenantKeys(
                    tenantId,
                    KeyPairEntry(
                        keyAlgorithm,
                        ByteBuffer.wrap(publicKey.encoded),
                        ByteBuffer.wrap(privateKey.encoded)
                    ),
                )
            )
        }
    }

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the key pair records to."
        ]
    )
    private var topic: String = TestSchema.CRYPTO_KEYS_TOPIC

    @Parameters(
        description = [
            "A file with the key-pair data."
        ]
    )
    internal lateinit var keyPairDataFile: File

    override fun call(): Collection<Record<String, TenantKeys>> {
        if (!keyPairDataFile.canRead()) {
            throw SetupException("Can not read data from $keyPairDataFile.")
        }
        val pairData = ConfigFactory.parseFile(keyPairDataFile)

        return listOf(
            pairData.toKeysRecord(topic)
        )
    }
}
