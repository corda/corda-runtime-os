package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.test.GroupPolicyEntry
import net.corda.schema.TestSchema
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "add-group",
    description = ["Create a membership group"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class AddGroup : Callable<Collection<Record<String, GroupPolicyEntry>>> {
    companion object {
        internal fun Config.toGroupRecord(topic: String = TestSchema.GROUP_POLICIES_TOPIC): Record<String, GroupPolicyEntry> {
            val groupId = this.getString("groupId")
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
                groupId,
                networkType,
                protocolMode,
                trustRootCertificates,
            )
            return Record(topic, entry.groupId, entry)
        }
    }

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the group information records to."
        ]
    )
    private var groupInfoTopic: String = TestSchema.GROUP_POLICIES_TOPIC

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
