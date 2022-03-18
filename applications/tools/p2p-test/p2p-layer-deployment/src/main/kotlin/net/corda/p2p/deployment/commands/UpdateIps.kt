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
        val loadBalancerIps by lazy {
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
                val servicesIps = ProcessRunner.execute(
                    "kubectl",
                    "get",
                    "service",
                    "-n",
                    name,
                    "-l=type=p2p-gateway",
                    "--output",
                    "jsonpath={.items[*].spec.clusterIP}",
                ).split(" ")
                    .filter { it.isNotBlank() }
                if (servicesIps.isEmpty()) {
                    return@lazy ProcessRunner.execute(
                        "kubectl",
                        "get",
                        "pod",
                        "-n",
                        name,
                        "-l=type=p2p-gateway",
                        "--output",
                        "jsonpath={.items[*].status.hostIP}",
                    ).split(" ")
                        .filter { it.isNotBlank() }
                        .also {
                            if (it.isEmpty()) {
                                throw DeploymentException("No load balancer service")
                            }
                        }
                } else {
                    return@lazy servicesIps
                }
            }
            if (ip == "None") {
                // We need to read the IPs :(
                // kubectl exec -n yift-sender p2p-gateway-1-5768fc74fd-5fxzd -- getent hosts load-balancer.yift-receiver
                // kubectl describe service  -n yift-receiver load-balancer
                // Sender: 192.168.145.245:1433,192.168.152.139:1433
                // Reciever: 192.168.135.255:1433,192.168.138.250:1433
                // kubectl exec -n yift-receiver svc/load-balancer -- getent hosts load-balancer.yift-sender
                ProcessRunner.execute(
                    "kubectl",
                    "exec",
                    "-n",
                    name,
                    "svc/load-balancer",
                    "--",
                    "getent",
                    "hosts",
                    "load-balancer.$name",
                ).lineSequence()
                    .filter { it.isNotBlank() }
                    .map {
                        it.split(' ')[0]
                    }.toList()
            } else {
                listOf(ip)
            }
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
            val ipMap = namespaces.flatMap { namespaceToGetIp ->
                namespaceToGetIp.loadBalancerIps.map { ip ->
                    mapOf(
                        "ip" to ip,
                        "hostnames" to listOf(namespaceToGetIp.host)
                    )
                }
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
