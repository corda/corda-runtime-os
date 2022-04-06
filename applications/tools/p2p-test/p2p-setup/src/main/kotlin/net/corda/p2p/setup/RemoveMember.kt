package net.corda.p2p.setup

import net.corda.messaging.api.records.Record
import net.corda.schema.TestSchema
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@Command(
    name = "remove-member",
    aliases = ["rm-member"],
    description = ["Remove a member from a group"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
)
class RemoveMember : Callable<Collection<Record<String, *>>> {

    @Option(
        names = ["--topic"],
        description = [
            "Topic to write the member information records to."
        ]
    )
    private var memberInfoTopic: String = TestSchema.MEMBER_INFO_TOPIC

    @Option(
        names = ["-x", "--x500", "--x500-name"],
        description = [
            "The x500 name"
        ]
    )
    private lateinit var x500Name: String

    @Option(
        names = ["-g", "--group", "--group-id"],
        description = [
            "The group ID"
        ]
    )
    private lateinit var groupId: String

    override fun call(): Collection<Record<String, *>> {
        return listOf(Record(memberInfoTopic, "$x500Name-$groupId", null))
    }
}
