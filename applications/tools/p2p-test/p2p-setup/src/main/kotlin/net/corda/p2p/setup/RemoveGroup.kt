package net.corda.p2p.setup

import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.Companion.GROUP_POLICIES_TOPIC
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable

@Command(
    name = "remove-group",
    aliases = ["rm-group"],
    description = ["Remove a membership group"],
    showAtFileInUsageHelp = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    usageHelpAutoWidth = true,
)
class RemoveGroup : Callable<Collection<Record<String, *>>> {

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the member information records to."
        ]
    )
    private var groupInfoTopic: String = GROUP_POLICIES_TOPIC

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
        return listOf(Record(groupInfoTopic, "$x500Name-$groupId", null))
    }
}
