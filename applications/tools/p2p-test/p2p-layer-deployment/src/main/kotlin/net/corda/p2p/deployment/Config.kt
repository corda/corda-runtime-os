package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File

@Command(
    name = "config",
    description = ["Apply new configuration"]
)
class Config : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"]
    )
    private var namespaceName = "p2p-layer"

    @Option(
        names = ["-f", "--file"],
        description = ["Copy file to configurator"]
    )
    private var filesToCopy = emptyList<File>()

    @Parameters
    private var configArguments = emptyList<String>()

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
            throw RuntimeException("Could not get pods")
        }

        val reader = ObjectMapper(YAMLFactory()).reader()
        val rawData = reader.readValue(getPods.inputStream, Map::class.java)
        val pods = rawData["items"] as List<Yaml>
        val configurator = pods.firstOrNull {
            val spec = it["spec"] as Yaml
            val containers = spec["containers"] as List<Yaml>
            val app = containers.firstOrNull()?.get("name")
            app == "configurator"
        } ?: throw RuntimeException("Can not find configurator")
        val metadata = configurator["metadata"] as Yaml
        val name = metadata["name"] as? String ?: throw RuntimeException("Can not find configurator name")

        val mkDir = ProcessBuilder().command(
            "kubectl",
            "exec",
            "-n", namespaceName,
            name,
            "--",
            "mkdir", "-p", "/opt/override/filesFromHost"
        ).inheritIO()
            .start()
        mkDir.waitFor()

        filesToCopy.forEach { file ->
            val cp = ProcessBuilder().command(
                "kubectl",
                "cp",
                file.absolutePath,
                "$namespaceName/$name:/opt/override/filesFromHost",
            ).inheritIO()
                .start()
            cp.waitFor()
        }

        val configCommand = listOf(
            "kubectl",
            "exec",
            "-n", namespaceName,
            name,
            "--",
            "java",
            "-jar",
            "./opt/override/jars/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar"
        ) + configArguments
        val config = ProcessBuilder().command(configCommand)
            .inheritIO()
            .start()
        config.waitFor()
    }
}
