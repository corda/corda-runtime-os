package net.corda.p2p.deployment.pods

class HeadlessNginxLoadBalancer(
    nginxCount: Int,
    servers: Collection<String>
) : Yamlable {
    private val pods = (1..nginxCount).map {
        NginxLoadBalancer(servers, false, it)
    }
    private val service = HeadlessLoadBalancer("nginx-load-balancer")

    override fun yamls(namespaceName: String) = pods.flatMap {
        it.yamls(namespaceName)
    } + service.yamls(namespaceName)
}
