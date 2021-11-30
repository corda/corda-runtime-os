package net.corda.p2p.deployment.pods

class LoadBalancer(
    override val hosts: Collection<String>,
    servers: Collection<String>,
) : Pod() {
    override val app = "load-balancer"
    override val image = "tekn0ir/nginx-stream"
    override val ports: Collection<Port> = listOf(
        Port("http", 1433)
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
                            "server $it:1433;"
                        }
                    }
                    }
                    server {
                        listen 1433;
                        proxy_pass loadbalancer;
                    }
                    """.trimIndent()
                )
            )
        )
    )
}
