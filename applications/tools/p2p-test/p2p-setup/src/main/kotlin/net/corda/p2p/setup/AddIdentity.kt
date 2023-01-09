package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "add-identity",
    description = ["Publish locally hosted identity"],
    showAtFileInUsageHelp = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class AddIdentity : Callable<Collection<Record<String, HostedIdentityEntry>>> {
    companion object {
        fun Config.toIdentityRecord(topic: String = P2P_HOSTED_IDENTITIES_TOPIC): Record<String, HostedIdentityEntry> {
            val x500Name = this.getString("x500Name")
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
            val publicSessionKey = try {
                dataConfig.getString("publicSessionKeyFile").let {
                    File(it).readText()
                }
            } catch (e: Missing) {
                dataConfig.getString("publicSessionKey")
            }
            publicSessionKey.verifyPublicKey()

            return Record(
                topic, "$x500Name-$groupId",
                HostedIdentityEntry(
                    HoldingIdentity(x500Name, groupId),
                    tlsTenantId,
                    sessionKeyTenantId,
                    tlsCertificates,
                    publicSessionKey,
                    null
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
    private var identityInfoTopic: String = P2P_HOSTED_IDENTITIES_TOPIC

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
