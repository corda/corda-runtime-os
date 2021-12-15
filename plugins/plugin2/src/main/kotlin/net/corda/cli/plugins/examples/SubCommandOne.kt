package net.corda.cli.plugins.examples

import picocli.CommandLine

@CommandLine.Command(name = "sub-command", description = ["Example subcommand."])
class SubCommandOne() : Runnable {
    override fun run() {
        println("Hello from ExamplePluginTwo")
    }
}