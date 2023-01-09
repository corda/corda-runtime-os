package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.test.KeyPairEntry
import net.corda.data.p2p.test.TenantKeys
import net.corda.schema.Schemas.P2P.Companion.CRYPTO_KEYS_TOPIC
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable

@Command(
    name = "add-key-pair",
    aliases = ["add-keys"],
    description = ["Add a key-pair"],
    showAtFileInUsageHelp = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class AddKeyPair : Callable<Collection<Record<String, TenantKeys>>> {
    companion object {
        fun Config.toKeysRecord(topic: String = CRYPTO_KEYS_TOPIC): Record<String, TenantKeys> {
            val publishAlias = try {
                this.getString("publishAlias")
            } catch (_: Missing) {
                UUID.randomUUID().toString()
            }

            val keys = try {
                val keysFile = this.getString("keysFile")
                File(keysFile).readText()
            } catch (e: Missing) {
                this.getString("keys")
            }
            keys.verifyKeyPair()

            val tenantId = this.getString("tenantId")

            return Record(
                topic, publishAlias,
                TenantKeys(
                    tenantId,
                    KeyPairEntry(
                        keys
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
    private var topic: String = CRYPTO_KEYS_TOPIC

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
