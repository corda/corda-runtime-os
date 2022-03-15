package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.DockerSecrets

class TestingOneLoadBalance(
    type: LbType,
    servers: Collection<String>,
) : Pod() {
    fun LbType.tag() = when (this) {
        LbType.KA -> "ka-1647339862"
        LbType.RA -> "ra-1647339889"
        LbType.RS -> "rs-1647339895"
        LbType.KS -> "ks-1647339857"
        else -> throw DeploymentException("Unknown LB type $this")
    }
    override val app = "load-balancer"
    override val image = "${DockerSecrets.cordaHost}/corda-p2p-test-lb:${type.tag()}"
    override val ports: Collection<Port> = listOf(
        Port.Gateway
    )
    override val environmentVariables = mapOf(
        "PORT" to Port.Gateway.port.toString(),
        "ENTRIES" to servers.map { "$it:${Port.Gateway.port}" }.joinToString(",")
    )
    override val readyLog = ".*Listening to.*".toRegex()
}
