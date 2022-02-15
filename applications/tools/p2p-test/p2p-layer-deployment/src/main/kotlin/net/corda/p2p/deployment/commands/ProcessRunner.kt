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

    fun kubeCtlGet(type: String, where: Collection<String>, what: Collection<String>): Collection<List<String>> {
        val (separator, newLine) = if (System.getProperty("os.name", "generic").startsWith("Windows")) {
            "{\\\"|\\\"}" to "{\\\"\\n\\\"}"
        } else {
            "{\"|\"}" to "{\"\\n\"}"
        }

        val jsonpath = "jsonpath={range .items[*]}${what.joinToString(separator) { "{.$it}" }}$newLine{end}"
        val baseCommand = arrayOf(
            "kubectl",
            "get",
            type
        ) + where.toTypedArray() + arrayOf(
            "-o",
            jsonpath
        )
        @Suppress("SpreadOperator")
        return execute(*baseCommand).lines()
            .filter { it.contains("|") }
            .map {
                it.split("|")
            }
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
