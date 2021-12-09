package net.corda.cli.plugins.examples

import picocli.CommandLine

@CommandLine.Command(name = "subCommand", description = ["Example subcommand."])
class SubCommandOne() : Runnable {
    override fun run() {
        println("Hello from ExamplePluginTwo")
    }
}