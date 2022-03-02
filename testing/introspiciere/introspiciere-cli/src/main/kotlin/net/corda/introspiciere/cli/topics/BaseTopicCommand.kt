package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.BaseCommand
import picocli.CommandLine

abstract class BaseTopicCommand : BaseCommand() {
    @CommandLine.Option(names = ["--topic"], required = true, description = ["Topic name"])
    protected lateinit var topicName: String
}