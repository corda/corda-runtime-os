package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.test.GroupPolicyEntry
import net.corda.schema.Schemas.P2P.Companion.GROUP_POLICIES_TOPIC
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "add-group",
    description = ["Create a membership group"],
    showAtFileInUsageHelp = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class AddGroup : Callable<Collection<Record<String, GroupPolicyEntry>>> {
    companion object {
        internal fun Config.toGroupRecord(topic: String = GROUP_POLICIES_TOPIC): Record<String, GroupPolicyEntry> {
            val groupId = this.getString("groupId")
            val x500Name = this.getString("x500Name")
            val dataConfig = this.getConfig("data")
            val networkType = dataConfig.getEnum(NetworkType::class.java, "networkType")
            val trustRootCertificates = try {
                dataConfig.getList("trustRootCertificatesFiles")
                    .unwrapped()
                    .filterIsInstance<String>()
                    .map {
                        File(it)
                    }.map { it.readText() }
            } catch (e: Missing) {
                dataConfig.getStringList("trustRootCertificates")
            }

            val protocolMode = dataConfig.getEnumList(ProtocolMode::class.java, "protocolModes")
            val entry = GroupPolicyEntry(
                HoldingIdentity(x500Name, groupId),
                networkType,
                protocolMode,
                trustRootCertificates,
            )
            return Record(topic, "$x500Name-$groupId", entry)
        }
    }

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the group information records to."
        ]
    )
    private var groupInfoTopic: String = GROUP_POLICIES_TOPIC

    @Parameters(
        description = [
            "A file with the group data."
        ]
    )
    internal lateinit var groupDataFile: File

    override fun call(): Collection<Record<String, GroupPolicyEntry>> {
        if (!groupDataFile.canRead()) {
            throw SetupException("Can not read data from $groupDataFile.")
        }
        val groupData = ConfigFactory.parseFile(groupDataFile)

        return listOf(groupData.toGroupRecord(groupInfoTopic))
    }
}
