package net.corda.p2p.fake.ca

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(
        @Suppress("SpreadOperator")
        CommandLine(Ca())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
    )
}
