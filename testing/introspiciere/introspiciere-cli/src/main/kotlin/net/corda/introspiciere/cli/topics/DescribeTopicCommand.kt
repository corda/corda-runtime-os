package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.stdout
import net.corda.introspiciere.cli.writeToYaml
import picocli.CommandLine

@CommandLine.Command(name = "describe")
class DescribeTopicCommand : BaseTopicCommand() {
    override fun run() {
        httpClient.describeTopic(topicName).writeToYaml(stdout)
    }
}