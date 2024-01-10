package net.corda.p2p.fake.ca

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println("\u001B[31mWarning\u001B[0m: This tool is not safe for production use, and it should only be used for testing purposes.")
    exitProcess(
        @Suppress("SpreadOperator")
        CommandLine(Ca())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setExecutionExceptionHandler(ExceptionHandler())
            .execute(*args)
    )
}
