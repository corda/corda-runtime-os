package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import kotlin.concurrent.thread

@Command(
    name = "log",
    description = ["print the logs of the pods in the namespace"]
)
class Log : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"]
    )
    private var namespaceName = "p2p-layer"

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

    @Suppress("UNCHECKED_CAST")
    private fun getAllPods(): Map<String, String> {
        val getPods = ProcessBuilder().command(
            "kubectl",
            "get",
            "pod",
            "-n", namespaceName,
            "-o", "yaml",
        ).start()
        if (getPods.waitFor() != 0) {
            System.err.println(getPods.errorStream.reader().readText())
            throw RuntimeException("Could not get pods")
        }

        val reader = ObjectMapper(YAMLFactory()).reader()
        val rawData = reader.readValue(getPods.inputStream, Map::class.java)
        val items = rawData["items"] as List<Yaml>
        return items.associate {
            val spec = it["spec"] as Yaml
            val containers = spec["containers"] as List<Yaml>
            val displayName =
                containers.firstOrNull()?.get("name") as? String ?: throw RuntimeException("Can not get pod name")
            val metadata = it["metadata"] as Yaml
            val podName = metadata["name"] as? String ?: throw RuntimeException("Could not find $displayName")
            displayName to podName
        }
    }
}
