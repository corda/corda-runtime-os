package net.corda.introspiciere.cli

import picocli.CommandLine

@CommandLine.Command(subcommands = [
    HelloWorldCommand::class,
    CreateTopicCommand::class
])
class Subcommands : Runnable {
    @CommandLine.Spec
    private lateinit var spec: CommandLine.Model.CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}