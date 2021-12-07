package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "psql",
    description = ["Interact with the DB SQL client"]
)
class Psql : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true,
    )
    lateinit private var namespaceName : String

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val getPods = ProcessBuilder().command(
            "kubectl",
            "get",
            "pod",
            "-n", namespaceName,
            "-o", "yaml",
        ).start()
        if (getPods.waitFor() != 0) {
            System.err.println(getPods.errorStream.reader().readText())
            throw DeploymentException("Could not get pods")
        }

        val reader = ObjectMapper(YAMLFactory()).reader()
        val rawData = reader.readValue(getPods.inputStream, Map::class.java)
        val items = rawData["items"] as List<Yaml>
        val name = items.firstOrNull {
            val spec = it["spec"] as Yaml
            val containers = spec["containers"] as List<Yaml>
            containers.firstOrNull()?.get("name") == "db"
        }?.let {
            val metadata = it["metadata"] as Yaml
            metadata["name"] as? String
        } ?: throw DeploymentException("Could not find database pod")

        val bash = ProcessBuilder().command(
            "kubectl",
            "exec",
            "-it",
            "-n",
            namespaceName,
            name,
            "--",
            "psql",
            "-U",
            "corda",
        )
            .inheritIO()
            .start()

        bash.waitFor()
    }
}
