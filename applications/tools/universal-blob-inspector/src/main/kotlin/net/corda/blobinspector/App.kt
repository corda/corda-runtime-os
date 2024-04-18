package net.corda.blobinspector

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(CommandLine(Command()).execute(*args))
}
