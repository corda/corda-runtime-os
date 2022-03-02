package net.corda.introspiciere.cli.topics

import picocli.CommandLine

@CommandLine.Command(name = "delete")
class DeleteTopicCommand : BaseTopicCommand() {
    override fun run() {
        httpClient.deleteTopic(topicName)
        println("Topic $topicName deleted successfully")
    }
}