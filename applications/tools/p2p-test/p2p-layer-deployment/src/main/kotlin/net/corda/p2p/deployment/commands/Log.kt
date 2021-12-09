package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import kotlin.concurrent.thread

@Command(
    name = "log",
    showDefaultValues = true,
    description = ["print the logs of the pods in the namespace"]
)
class Log : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    private lateinit var namespaceName: String

    @Option(
        names = ["-p", "--pods"],
        description = ["regular expression for the pods names"]
    )
    private var pod: String = ""

    @Option(
        names = ["-f", "--follow"],
        description = ["Follow after the logs"]
    )
    private var follow: Boolean = false

    override fun run() {
        val filter = Regex(".*$pod.*")
        getAllPods().filter { (displayName, _) ->
            filter.matches(displayName)
        }.map { (displayName, podName) ->
            thread {
                logPod(displayName, podName)
            }
        }.forEach {
            it.join()
        }
    }

    private fun logPod(displayName: String, podName: String) {
        val command = listOf(
            "kubectl",
            "logs",
            "-n", namespaceName,
            podName,
        ) + if (follow) {
            listOf("-f")
        } else {
            emptyList()
        }
        val logPod = ProcessBuilder().command(
            command
        ).start()
        thread(isDaemon = true) {
            logPod.inputStream.reader().useLines { lines ->
                lines.forEach { line ->
                    println("$displayName: $line")
                }
            }
        }
        logPod.waitFor()
    }

    @Suppress("UNCHECKED_CAST", "ThrowsCount")
    private fun getAllPods(): Map<String, String> {
        val getPods = ProcessBuilder().command(
            "kubectl",
            "get",
            "pod",
            "-n", namespaceName,
            "--output",
            "jsonpath={range .items[*]}{.metadata.name}{\",\"}{.spec.containers[].name}{\"\\n\"}{end}"
        ).start()
        if (getPods.waitFor() != 0) {
            System.err.println(getPods.errorStream.reader().readText())
            throw DeploymentException("Could not get pods")
        }
        return getPods
            .inputStream
            .reader()
            .readLines()
            .map {
                it.split(",")
            }.map {
                it[1] to it[0]
            }.toMap()
    }
}
