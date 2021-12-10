package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import kotlin.concurrent.thread

object ProcessRunner {
    fun execute(vararg command: String): String {
        val process = ProcessBuilder()
            .command(*command)
            .start()
        val output = process.inputStream.reader().readText()
        if (process.waitFor() != 0) {
            System.err.println(process.errorStream.reader().readText())
            throw DeploymentException("Could not run process")
        }
        return output
    }

    fun follow(vararg command: String): Boolean {
        return follow(command.toList())
    }

    fun follow(command: List<String>): Boolean {
        val process = ProcessBuilder()
            .command(command)
            .inheritIO()
            .start()
        return process.waitFor() == 0
    }

    fun runWithInputs(command: List<String>, inputs: ByteArray) {
        val process = ProcessBuilder()
            .command(command)
            .start()
        thread(isDaemon = true) {
            process.inputStream.reader().useLines {
                it.forEach { line ->
                    println(line)
                }
            }
        }
        thread(isDaemon = true) {
            process.errorStream.reader().useLines {
                it.forEach { line ->
                    System.err.println(line)
                }
            }
        }
        process.outputStream.write(inputs)
        process.outputStream.close()
        if (process.waitFor() != 0) {
            throw DeploymentException("Could not deploy")
        }
    }
}
