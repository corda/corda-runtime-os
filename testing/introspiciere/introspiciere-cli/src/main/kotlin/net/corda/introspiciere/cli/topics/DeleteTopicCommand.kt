package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.stdout
import net.corda.introspiciere.cli.writeText
import picocli.CommandLine

@CommandLine.Command(name = "delete")
class DeleteTopicCommand : BaseTopicCommand() {

    companion object {
        val successMessage = "Topic %s deleted successfully"
    }

    override fun run() {
        httpClient.deleteTopic(topicName)
        successMessage.format(topicName).let(stdout::writeText)
    }
}