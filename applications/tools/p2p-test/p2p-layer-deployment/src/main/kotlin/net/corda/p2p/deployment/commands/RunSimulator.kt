package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Option
import java.io.File

abstract class RunSimulator : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    lateinit var namespaceName: String

    abstract val parameters: Yaml
    abstract val filePrefix: String

    @Suppress("UNCHECKED_CAST")
    val pods by lazy {
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
        rawData["items"] as List<Yaml>
    }

    @Suppress("UNCHECKED_CAST")
    val dbParams by lazy {
        pods.mapNotNull {
            val spec = it["spec"] as Yaml
            val containers = spec["containers"] as List<Yaml>
            containers.firstOrNull()
        }.firstOrNull {
            it["name"] == "db"
        }?.let {
            it["env"] as? Collection<Yaml>
        }?.let {
            val password = it.firstOrNull {
                it["name"] == "POSTGRES_PASSWORD"
            }?.let {
                it["value"] as? String
            }
            val username = it.firstOrNull {
                it["name"] == "POSTGRES_USER"
            }?.let {
                it["value"] as? String
            }
            mapOf(
                "username" to username,
                "password" to password,
                "host" to "db.$namespaceName",
                "db" to username
            )
        } ?: throw DeploymentException("Could not find database parameters")
    }

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val name = pods.firstOrNull {
            val spec = it["spec"] as Yaml
            val containers = spec["containers"] as List<Yaml>
            containers.firstOrNull()?.get("name") == "app-simulator-1024"
        }?.let {
            val metadata = it["metadata"] as Yaml
            metadata["name"] as? String
        } ?: throw DeploymentException("Could not find simulator")

        val file = File.createTempFile(filePrefix, ".conf")
        file.deleteOnExit()
        ObjectMapper().writeValue(file, parameters)
        val cpCommand = listOf(
            "kubectl",
            "cp",
            file.absolutePath,
            "$namespaceName/$name:/tmp"
        )
        val cp = ProcessBuilder().command(cpCommand)
            .start()
        if (cp.waitFor() != 0) {
            System.err.println(cp.errorStream.reader().readText())
            throw DeploymentException("Could not copy configuration file")
        }

        val command = listOf(
            "kubectl",
            "exec",
            "-it",
            "-n",
            namespaceName,
            name,
            "--",
            "java",
            "-jar",
            "/opt/override/app-simulator.jar",
            "--simulator-config",
            "/tmp/${file.name}"
        )

        val simulator = ProcessBuilder().command(command)
            .inheritIO()
            .start()

        simulator.waitFor()

        ProcessBuilder().command(
            "kubectl",
            "exec",
            "-n",
            namespaceName,
            name,
            "--",
            "rm",
            "-f",
            "/tmp/${file.name}"
        )
            .inheritIO()
            .start()
            .waitFor()
    }
}
