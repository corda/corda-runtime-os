package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.p2p.test.MemberInfoEntry
import net.corda.schema.TestSchema
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "add-member",
    description = ["Add a group member"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
)
class AddMember : Callable<Collection<Record<String, MemberInfoEntry>>> {
    companion object {
        fun Config.toMemberRecord(topic: String = TestSchema.MEMBER_INFO_TOPIC): Record<String, MemberInfoEntry> {
            val x500Name = this.getString("x500name")
            val groupId = this.getString("groupId")
            val dataConfig = this.getConfig("data")
            val address = dataConfig.getString("address")

            val publicSessionKey = try {
                val publicKeyFile = dataConfig.getString("publicSessionKeyFile")
                File(publicKeyFile).readText()
            } catch (e: Missing) {
                dataConfig.getString("publicSessionKey")
            }
            publicSessionKey.verifyPublicKey()
            val networkMapEntry = MemberInfoEntry(
                HoldingIdentity(x500Name, groupId),
                publicSessionKey,
                address,
            )
            return Record(topic, "$x500Name-$groupId", networkMapEntry)
        }
    }

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the member information records to."
        ]
    )
    private var memberInfoTopic: String = TestSchema.MEMBER_INFO_TOPIC

    @Parameters(
        description = [
            "A file with the member data."
        ]
    )
    internal lateinit var memberDataFile: File

    override fun call(): Collection<Record<String, MemberInfoEntry>> {
        if (!memberDataFile.canRead()) {
            throw SetupException("Can not read data from $memberDataFile.")
        }
        val memberData = ConfigFactory.parseFile(memberDataFile)

        return listOf(
            memberData.toMemberRecord(memberInfoTopic)
        )
    }
}
