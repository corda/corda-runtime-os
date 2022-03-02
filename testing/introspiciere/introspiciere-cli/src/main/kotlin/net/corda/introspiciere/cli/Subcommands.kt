package net.corda.introspiciere.cli

import net.corda.introspiciere.cli.topics.TopicCommands
import picocli.CommandLine

@CommandLine.Command(subcommands = [
    HelloWorldCommand::class,
    TopicCommands::class,
    WriteCommand::class,
    ReadCommand::class
])
class Subcommands : Runnable {
    @CommandLine.Spec
    private lateinit var spec: CommandLine.Model.CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}