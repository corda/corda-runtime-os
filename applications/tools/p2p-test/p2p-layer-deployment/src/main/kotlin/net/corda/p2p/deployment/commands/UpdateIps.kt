package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Command

@Command(
    name = "update-ips",
    showDefaultValues = true,
    description = ["Update all the ips in all the gateways"]
)
class UpdateIps : Runnable {
    private val yaml = ObjectMapper(YAMLFactory())
    private val yamlReader = yaml.reader()
    private val yamlWriter = yaml.writer()

    private inner class Namespace(
        val name: String,
        val host: String,
    ) {

        @Suppress("UNCHECKED_CAST")
        val loadBalancerIp by lazy {
            val getAll = ProcessBuilder().command(
                "kubectl",
                "get",
                "service",
                "-n",
                name,
                "-o",
                "yaml"
            ).start()
            if (getAll.waitFor() != 0) {
                System.err.println(getAll.errorStream.reader().readText())
                throw DeploymentException("Could not get services")
            }
            val rawData = yamlReader.readValue(getAll.inputStream, Map::class.java)
            val items = rawData["items"] as List<Yaml>
            val spec = items.first {
                val metadata = it["metadata"] as Yaml
                metadata["name"] == "load-balancer"
            }["spec"] as Yaml
            spec["clusterIP"] as String
        }

        @Suppress("UNCHECKED_CAST")
        val gateways by lazy {
            val getAll = ProcessBuilder().command(
                "kubectl",
                "get",
                "pod",
                "-n",
                name,
                "-o",
                "yaml"
            ).start()
            if (getAll.waitFor() != 0) {
                System.err.println(getAll.errorStream.reader().readText())
                throw DeploymentException("Could not get services")
            }
            val rawData = yamlReader.readValue(getAll.inputStream, Map::class.java)
            val items = rawData["items"] as List<Yaml>
            items.asSequence().map {
                it["metadata"] as Yaml
            }.mapNotNull {
                val labels = it["labels"] as? Yaml
                labels?.get("app") as? String
            }.filter {
                it.startsWith("p2p-gateway")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun namespaces(): Collection<Namespace> {
        val getAll = ProcessBuilder().command(
            "kubectl",
            "get",
            "namespace",
            "-o",
            "yaml"
        ).start()
        if (getAll.waitFor() != 0) {
            System.err.println(getAll.errorStream.reader().readText())
            throw DeploymentException("Could not get namespaces")
        }
        val rawData = yamlReader.readValue(getAll.inputStream, Map::class.java)
        val items = rawData["items"] as List<Yaml>
        return items.map {
            it["metadata"] as Yaml
        }.filter {
            val annotations = it["annotations"] as? Yaml
            annotations?.get("type") == "p2p"
        }.map {
            val annotations = it["annotations"] as Yaml
            val host = annotations["host"] as String
            val name = it["name"] as String
            Namespace(name, host)
        }
    }

    override fun run() {
        val namespaces = namespaces()
        namespaces.forEach { namespaceToPatch ->
            val ipMap = namespaces.map { namespaceToGetIp ->
                val ip = if (namespaceToPatch == namespaceToGetIp) {
                    "0.0.0.0"
                } else {
                    namespaceToGetIp.loadBalancerIp
                }
                mapOf(
                    "ip" to ip,
                    "hostnames" to listOf(namespaceToGetIp.host)
                )
            }
            val conf = mapOf(
                "spec" to
                    mapOf(
                        "template" to
                            mapOf(
                                "spec" to
                                    mapOf(
                                        "hostAliases" to ipMap
                                    )
                            )
                    )
            )
            namespaceToPatch.gateways.forEach { gatewayName ->
                println("Setting IP map of $gatewayName in ${namespaceToPatch.name}")
                val patch = ProcessBuilder()
                    .command(
                        "kubectl",
                        "patch",
                        "deployment",
                        gatewayName,
                        "-n",
                        namespaceToPatch.name,
                        "--patch",
                        yamlWriter.writeValueAsString(conf)
                    ).inheritIO().start()
                if (patch.waitFor() != 0) {
                    throw DeploymentException("Could not patch gateway $gatewayName in ${namespaceToPatch.name}")
                }
            }
        }
    }
}
