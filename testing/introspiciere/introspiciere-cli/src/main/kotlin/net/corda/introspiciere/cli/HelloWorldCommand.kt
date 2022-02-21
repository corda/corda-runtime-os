package net.corda.introspiciere.cli

import net.corda.introspiciere.http.HelloWorldReq
import picocli.CommandLine

/**
 * Command to verify the client-server connection. Will be gone eventually.
 */
@CommandLine.Command(name = "helloworld")
class HelloWorldCommand : BaseCommand() {
    override fun run() {
        println(HelloWorldReq(endpoint).greetings())
    }
}