package net.corda.p2p.deployment.pods

class LoadBalancer(
    servers: Collection<String>,
) : Pod() {
    override val app = "load-balancer"
    override val image = "tekn0ir/nginx-stream"
    override val ports: Collection<Port> = listOf(
        Port.Gateway
    )
    override val rawData = listOf(
        TextRawData(
            "balancer-config", "/opt/nginx/stream.conf.d",
            listOf(
                TextFile(
                    "default.conf",
                    """
                    upstream loadbalancer {
                          ${
                    servers.joinToString("\n") {
                        "server $it:${Port.Gateway.port};"
                    }
                    }
                    }
                    server {
                        listen ${Port.Gateway.port};
                        proxy_pass loadbalancer;
                    }
                    """.trimIndent()
                )
            )
        )
    )
}
