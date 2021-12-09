package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import java.io.File

class CopyJars(
    private val namespaceName: String
) : Runnable {
    private val jars by lazy {
        File(".").walkBottomUp().filter {
            it.isFile
        }.filter {
            it.extension == "jar"
        }.filter {
            it.path.contains("build/bin")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val pods by lazy {
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
        items.mapNotNull {
            val metadata = it["metadata"] as Yaml
            metadata["name"] as? String
        }
    }
    private fun copyJar(name: String, run: Boolean) {
        val jar = jars.first {
            it.name.startsWith("corda-$name")
        }
        val pods = pods.filter { it.startsWith(name) }
        pods.forEach { podName ->
            println("Copying $jar to $podName...")
            val cpCommand = listOf(
                "kubectl",
                "cp",
                jar.absolutePath,
                "$namespaceName/$podName:/opt/override/$name.jar"
            )
            val cp = ProcessBuilder().command(cpCommand)
                .start()
            if (cp.waitFor() != 0) {
                System.err.println(cp.errorStream.reader().readText())
                throw DeploymentException("Could not copy jar file")
            }

            if (run) {
                println("Running $jar in $podName...")
                val command = listOf(
                    "kubectl",
                    "exec",
                    "-it",
                    "-n",
                    namespaceName,
                    podName,
                    "--",
                    "java",
                    "-jar",
                    "/opt/override/$name.jar",
                )
                ProcessBuilder().command(command)
                    .start()
            }
        }
    }
    override fun run() {
        copyJar("p2p-gateway", true)
        copyJar("p2p-link-manager", true)
        copyJar("app-simulator", false)
    }
}
