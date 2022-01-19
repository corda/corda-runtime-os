package net.corda.introspiciere.cli

import net.corda.introspiciere.http.HelloWorldReq
import picocli.CommandLine

fun main(vararg args: String) {
    CommandLine(HelloWorldCommand()).execute(*args)
}

class HelloWorldCommand : Runnable {
    @CommandLine.Option(names = ["--endpoint"], defaultValue = "http://localhost:7070")
    lateinit var endpoint: String

    override fun run() {
        println(HelloWorldReq(endpoint).greetings())
    }
}
