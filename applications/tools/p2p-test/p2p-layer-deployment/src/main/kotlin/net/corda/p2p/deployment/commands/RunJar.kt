package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Port
import java.io.File
import java.nio.file.Files

class RunJar(
    private val jarName: String,
    private val arguments: Collection<String>
) : Runnable {
    companion object {
        fun startTelepresence() {
            ProcessBuilder()
                .command(
                    "telepresence",
                    "connect"
                )
                .inheritIO()
                .start()
                .waitFor()
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
                val getAll = ProcessBuilder().command(
                    "kubectl",
                    "get",
                    "service",
                    "-n",
                    namespace,
                    "-l", "type=kafka-broker",
                    "--output", "jsonpath={.items[*].metadata.name}",
                ).start()
                if (getAll.waitFor() != 0) {
                    System.err.println(getAll.errorStream.reader().readText())
                    throw DeploymentException("Could not get services")
                }
                getAll
                    .inputStream
                    .reader()
                    .readText()
                    .split(" ")
                    .filter {
                        it.isNotBlank()
                    }.joinToString(",") {
                        "$it.$namespace:${Port.KafkaExternalBroker.port}"
                    }
            }
        }

        private val kafkaFiles = mutableMapOf<String, File>()
        fun kafkaFile(namespace: String): File {
            return kafkaFiles.computeIfAbsent(namespace) {
                File.createTempFile("$namespace.kafka.", ".properties").also { file ->
                    file.deleteOnExit()
                    file.delete()
                    file.writeText("bootstrap.servers=${kafkaServers(namespace)}")
                }
            }
        }
    }
    override fun run() {
        val jarFile = jarToRun(jarName)
        val java = "${System.getProperty("java.home")}/bin/java"
        val commands = listOf(java, "-jar", jarFile.absolutePath) + arguments
        ProcessBuilder()
            .command(
                commands
            )
            .inheritIO()
            .start()
            .waitFor()
    }
}
