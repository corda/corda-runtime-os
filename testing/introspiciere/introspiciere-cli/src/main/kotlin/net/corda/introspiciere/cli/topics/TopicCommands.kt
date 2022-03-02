package net.corda.introspiciere.cli.topics

import picocli.CommandLine

@CommandLine.Command(name = "topics", subcommands = [
    CreateTopicCommand::class,
    DeleteTopicCommand::class,
    DescribeTopicCommand::class,
    ListTopicCommand::class
])
class TopicCommands : Runnable {
    override fun run() {
        println("We should print usage here")
    }
}