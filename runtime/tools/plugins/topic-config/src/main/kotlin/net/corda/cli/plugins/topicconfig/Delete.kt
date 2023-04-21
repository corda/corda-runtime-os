package net.corda.cli.plugins.topicconfig

import picocli.CommandLine

@CommandLine.Command(name = "delete", description = ["Delete Kafka topics"], subcommands = [DeleteScript::class, DeleteConnect::class])
class Delete {

    @CommandLine.ParentCommand
    var topic: TopicPlugin.Topic? = null

}
