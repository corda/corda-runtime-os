package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.appendToStdout
import picocli.CommandLine

@CommandLine.Command(name = "delete")
class DeleteTopicCommand : BaseTopicCommand() {

    companion object {
        internal val successMessage = "Topic %s deleted successfully"
    }

    override fun run() {
        httpClient.deleteTopic(topicName)
        String.format(successMessage, topicName).let(::appendToStdout)
    }
}