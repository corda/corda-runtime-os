package net.corda.cli.plugins.network.output

import java.io.PrintStream

interface Output {
    fun generateOutput(content: String)
}

class ConsoleOutput(private val printStream: PrintStream = System.out) : Output {
    override fun generateOutput(content: String) {
        content.lines().forEach {
            printStream.println(it)
        }
    }
}
