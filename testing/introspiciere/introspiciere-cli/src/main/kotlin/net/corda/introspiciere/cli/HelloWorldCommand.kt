package net.corda.introspiciere.cli

import picocli.CommandLine

/**
 * Command to verify the client-server connection. Will be gone eventually.
 */
@CommandLine.Command(name = "helloworld")
class HelloWorldCommand : BaseCommand() {
    override fun run() {
        println(httpClient.greetings())
    }
}