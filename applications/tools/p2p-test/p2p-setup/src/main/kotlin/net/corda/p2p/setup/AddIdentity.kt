package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.p2p.test.HostedIdentityEntry
import net.corda.schema.TestSchema
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "add-identity",
    description = ["Publish locally hosted identity"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
)
class AddIdentity : Callable<Collection<Record<String, HostedIdentityEntry>>> {
    companion object {
        fun Config.toIdentityRecord(topic: String = TestSchema.HOSTED_MAP_TOPIC): Record<String, HostedIdentityEntry> {
            val x500Name = this.getString("x500name")
            val groupId = this.getString("groupId")
            val dataConfig = this.getConfig("data")
            val tlsTenantId = dataConfig.getString("tlsTenantId")
            val sessionKeyTenantId = dataConfig.getString("sessionKeyTenantId")
            val tlsCertificates = try {
                dataConfig.getStringList("tlsCertificatesFiles").map {
                    File(it)
                }.map {
                    it.readText()
                }
            } catch (e: Missing) {
                dataConfig.getStringList("tlsCertificates")
            }
            val publicKey = try {
                dataConfig.getString("publicKeyFile").let {
                    File(it).readText()
                }
            } catch (e: Missing) {
                dataConfig.getString("publicKey")
            }
            publicKey.verifyPublicKey()

            return Record(
                topic, "$x500Name-$groupId",
                HostedIdentityEntry(
                    HoldingIdentity(x500Name, groupId),
                    tlsTenantId,
                    sessionKeyTenantId,
                    tlsCertificates,
                    publicKey,
                )
            )
        }
    }

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the locally hosted identity information records to."
        ]
    )
    private var identityInfoTopic: String = TestSchema.HOSTED_MAP_TOPIC

    @Parameters(
        description = [
            "A file with the identity data."
        ]
    )
    internal lateinit var identityDataFile: File

    override fun call(): Collection<Record<String, HostedIdentityEntry>> {
        if (!identityDataFile.canRead()) {
            throw SetupException("Can not read data from $identityDataFile.")
        }
        val identityData = ConfigFactory.parseFile(identityDataFile)

        return listOf(
            identityData.toIdentityRecord(identityInfoTopic)
        )
    }
}
