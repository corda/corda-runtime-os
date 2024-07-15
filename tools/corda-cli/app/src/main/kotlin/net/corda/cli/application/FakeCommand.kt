package net.corda.cli.application

import picocli.CommandLine


@CommandLine.Command(
    name = "fake",
    mixinStandardHelpOptions = true,
    description = ["Fake command"],
)
class FakeCommand : Runnable {
    override fun run() {
        println("Hello from fake command")
    }
}
