package net.corda.cli.commands.network.output

interface Output {
    fun generateOutput(content: String)
}

class ConsoleOutput : Output {
    override fun generateOutput(content: String) {
        content.lines().forEach {
            println(it)
        }
    }
}
