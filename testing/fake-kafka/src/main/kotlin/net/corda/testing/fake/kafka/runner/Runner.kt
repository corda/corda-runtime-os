package net.corda.testing.fake.kafka.runner

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    exitProcess(CommandLine(KafkaRunner()).execute(*args))
}