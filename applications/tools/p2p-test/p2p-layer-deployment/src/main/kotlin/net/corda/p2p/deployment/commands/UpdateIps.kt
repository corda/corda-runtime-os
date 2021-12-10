package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import picocli.CommandLine.Command

@Command(
    name = "update-ips",
    showDefaultValues = true,
    description = ["Update all the ips in all the gateways"],
    mixinStandardHelpOptions = true,
)
class UpdateIps : Runnable {
    private val yaml = ObjectMapper(YAMLFactory())
    private val yamlWriter = yaml.writer()

    private inner class Namespace(
        val name: String,
        val host: String,
    ) {

        @Suppress("UNCHECKED_CAST")
        val loadBalancerIp by lazy {
            val ip = ProcessRunner.execute(
                "kubectl",
                "get",
                "service",
                "-n",
                name,
                "--field-selector",
                "metadata.name=load-balancer",
                "--output",
                "jsonpath={.items[*].spec.clusterIP}",
            )
            if (ip.isBlank()) {
                throw DeploymentException("No load balancer service")
            }
            ip
        }

        @Suppress("UNCHECKED_CAST")
        val gateways by lazy {
            ProcessRunner.execute(
                "kubectl",
                "get",
                "pod",
                "-n",
                name,
                "-l",
                "type=p2p-gateway",
                "--output",
                "jsonpath={.items[*].metadata.labels.app}",
            )
                .split(" ")
                .filter {
                    it.isNotBlank()
                }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun namespaces(): Collection<Namespace> {
        return ProcessRunner.execute(
            "kubectl",
            "get",
            "namespace",
            "-l",
            "namespace-type=p2p-deployment,creator=${MyUserName.userName}",
            "-o",
            "jsonpath={range .items[*]}{.metadata.name}{\",\"}{.metadata.annotations.host}{\"\\n\"}{end}",
        ).lines()
            .filter {
                it.contains(",")
            }
            .map { line ->
                val split = line.split(",")
                Namespace(split[0], split[1])
            }
    }

    override fun run() {
        val namespaces = namespaces()
        namespaces.forEach { namespaceToPatch ->
            val ipMap = namespaces.map { namespaceToGetIp ->
                mapOf(
                    "ip" to namespaceToGetIp.loadBalancerIp,
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
                val patched = ProcessRunner.follow(
                    "kubectl",
                    "patch",
                    "deployment",
                    gatewayName,
                    "-n",
                    namespaceToPatch.name,
                    "--patch",
                    yamlWriter.writeValueAsString(conf)
                )
                if (!patched) {
                    throw DeploymentException("Could not patch gateway $gatewayName in ${namespaceToPatch.name}")
                }
            }
        }
    }
}
