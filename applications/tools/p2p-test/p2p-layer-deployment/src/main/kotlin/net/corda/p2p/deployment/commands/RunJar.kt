package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Port
import java.io.File
import java.nio.file.Files

class RunJar(
    private val jarName: String,
    private val arguments: Collection<String>,
    private val properties: Map<String, String> = emptyMap(),
) : Runnable {
    companion object {
        fun startTelepresence() {
            ProcessRunner.follow(
                "telepresence",
                "connect"
            )
        }

        private val savedJar = mutableMapOf<String, File>()
        private fun jarToRun(jarName: String): File {
            return savedJar.computeIfAbsent(jarName) {
                File.createTempFile(jarName, ".jar").also { jarFile ->
                    jarFile.deleteOnExit()
                    jarFile.delete()
                    ClassLoader.getSystemClassLoader()
                        .getResource("$jarName.jar")
                        ?.openStream()?.use { input ->
                            Files.copy(input, jarFile.toPath())
                        }
                }
            }
        }
        private val kafkaServers = mutableMapOf<String, String>()

        fun kafkaServers(namespace: String): String {
            return kafkaServers.computeIfAbsent(namespace) {
                ProcessRunner.execute(
                    "kubectl",
                    "get",
                    "service",
                    "-n",
                    namespace,
                    "-l", "type=kafka-broker",
                    "--output", "jsonpath={.items[*].metadata.name}",
                )
                    .split(" ")
                    .filter {
                        it.isNotBlank()
                    }.joinToString(",") {
                        "$it.$namespace:${Port.KafkaExternalBroker.port}"
                    }
            }
        }
    }
    override fun run() {
        val jarFile = jarToRun(jarName)
        val java = "${System.getProperty("java.home")}/bin/java"
        val propertiesList =
            properties.map { (name, value) ->
                "-D$name=$value"
            }

        val commands = listOf(java) +
            propertiesList +
            listOf("-jar", jarFile.absolutePath) +
            arguments
        val success = ProcessRunner.follow(
            commands
        )
        if (!success) {
            throw DeploymentException("Can not run ${arguments.joinToString()}")
        }
    }
}
