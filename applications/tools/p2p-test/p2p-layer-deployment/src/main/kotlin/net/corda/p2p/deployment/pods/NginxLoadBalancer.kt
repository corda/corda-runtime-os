package net.corda.p2p.deployment.pods

class NginxLoadBalancer(
    servers: Collection<String>,
    exportPort: Boolean,
    index: Int? = null,
) : Pod() {
    override val app by lazy {
        if (index == null) {
            "load-balancer"
        } else {
            "load-balancer-$index"
        }
    }

    override val image = "tekn0ir/nginx-stream"
    override val ports: Collection<Port> = if (exportPort) {
        listOf(
            Port.Gateway
        )
    } else {
        emptyList()
    }

    override val labels by lazy {
        mapOf("type" to "nginx-load-balancer")
    }

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
