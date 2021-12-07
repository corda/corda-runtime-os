package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "bash",
    description = ["Bash into one of the pods"],
    showDefaultValues = true,
)
class Bash : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true,
    )
    private lateinit var namespaceName: String

    @Option(
        names = ["-p", "--pod"],
        description = ["The name of the pod"]
    )
    private var pod = "p2p-gateway-1"

    @Parameters(
        description = ["The command (with parameters) to run (default to bash)"]
    )
    private var params = listOf("bash")

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
            containers.firstOrNull()?.get("name") == pod
        }?.let {
            val metadata = it["metadata"] as Yaml
            metadata["name"] as? String
        } ?: throw DeploymentException("Could not find $pod")

        val command = listOf(
            "kubectl",
            "exec",
            "-it",
            "-n",
            namespaceName,
            name,
            "--",
        ) + if (params.isEmpty()) {
            listOf("bash")
        } else {
            params
        }

        val bash = ProcessBuilder().command(command)
            .inheritIO()
            .start()

        bash.waitFor()
    }
}
