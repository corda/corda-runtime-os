package net.corda.p2p.setup

import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.Companion.MEMBER_INFO_TOPIC
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@Command(
    name = "remove-member",
    aliases = ["rm-member"],
    description = ["Remove a member from a group"],
    showAtFileInUsageHelp = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class RemoveMember : Callable<Collection<Record<String, *>>> {

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the member information records to."
        ]
    )
    private var memberInfoTopic: String = MEMBER_INFO_TOPIC

    @Option(
        names = ["-x", "--x500", "--x500-name"],
        description = [
            "The x500 name"
        ],
        required = true,
    )
    private lateinit var x500Name: String

    @Option(
        names = ["-g", "--group", "--group-id"],
        description = [
            "The group ID"
        ],
        required = true,
    )
    private lateinit var groupId: String

    override fun call(): Collection<Record<String, *>> {
        return listOf(Record(memberInfoTopic, "$x500Name-$groupId", null))
    }
}
