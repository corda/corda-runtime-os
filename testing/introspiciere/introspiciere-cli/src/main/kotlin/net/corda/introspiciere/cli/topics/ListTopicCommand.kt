package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.BaseCommand
import net.corda.introspiciere.cli.stdout
import net.corda.introspiciere.cli.writeOnePerLine
import picocli.CommandLine

@CommandLine.Command(name = "list")
class ListTopicCommand : BaseCommand() {
    override fun run() {
        httpClient.listTopics().writeOnePerLine(stdout)
    }
}