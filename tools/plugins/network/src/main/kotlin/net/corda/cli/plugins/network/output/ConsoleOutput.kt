package net.corda.cli.plugins.network.output

import java.io.PrintStream

interface Output {
    fun generateOutput(content: String)
}

class ConsoleOutput(private val printStream: PrintStream? = null) : Output {
    override fun generateOutput(content: String) {
        content.lines().forEach {
            printStream?.println(it) ?: println(it)
        }
    }
}
