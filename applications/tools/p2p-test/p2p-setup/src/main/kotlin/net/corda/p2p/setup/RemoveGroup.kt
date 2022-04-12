package net.corda.p2p.setup

import net.corda.messaging.api.records.Record
import net.corda.schema.TestSchema
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable

@Command(
    name = "remove-group",
    aliases = ["rm-group"],
    description = ["Remove a membership group"],
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
    private var groupInfoTopic: String = TestSchema.GROUP_POLICIES_TOPIC

    @Parameters(
        description = [
            "The group ID."
        ]
    )
    internal lateinit var groupId: String

    override fun call(): Collection<Record<String, *>> {
        return listOf(Record(groupInfoTopic, groupId, null))
    }
}
