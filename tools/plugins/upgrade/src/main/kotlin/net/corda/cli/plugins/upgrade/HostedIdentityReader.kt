package net.corda.cli.plugins.upgrade

import picocli.CommandLine

@CommandLine.Command(
    name = "read-kafka",
    description = ["Read hosted identity records from Kafka"],
    mixinStandardHelpOptions = true,
)
class HostedIdentityReader : Runnable {
    override fun run() {
        println("Ran command: 'upgrade' subcommand: 'read-kafka'.")
    }
}
