package net.corda.p2p.deployment.pods

class LoadBalancer(
    override val hosts: Collection<String>,
    servers: Collection<String>,
) : Pod() {
    override val app = "load-balancer"
    override val image = "nginx"
    override val ports: Collection<Port> = listOf(
        Port("http", 80)
    )
    override val rawData = listOf(
        TextRawData(
            "balancer-config", "/etc/nginx/conf.d/",
            listOf(
                TextFile(
                    "default.conf",
                    """
                        upstream loadbalancer {
                          ${servers.map {
                        "server $it:80;"
                    }.joinToString("\n")}
                        }
                        server {
                          location / {
                          proxy_pass http://loadbalancer;
                        }}
                    """.trimIndent()
                )
            )
        )
    )
}
