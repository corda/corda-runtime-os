package net.corda.e2etest.utilities

import java.net.URI

abstract class ClusterConfig {
    abstract val clusterId: String

    private companion object {
        private const val DEFAULT_REST_HOST = "localhost"
        private const val DEFAULT_REST_PORT = 8888
        private const val DEFAULT_P2P_HOST = "localhost"
        private const val DEFAULT_P2P_PORT = 8080
    }

    private val restHostPropertyName = "E2E_CLUSTER_${clusterId}_RPC_HOST"
    private val restPortPropertyName = "E2E_CLUSTER_${clusterId}_RPC_PORT"
    private val restPasswordPropertyName = "E2E_CLUSTER_${clusterId}_RPC_PASSWORD"
    private val p2pHostPropertyName = "E2E_CLUSTER_${clusterId}_P2P_HOST"
    private val p2pPortPropertyName = "E2E_CLUSTER_${clusterId}_P2P_PORT"

    val restHost: String get() = System.getenv(restHostPropertyName) ?: DEFAULT_REST_HOST
    val restPort: Int get() = System.getenv(restPortPropertyName)?.toInt() ?: DEFAULT_REST_PORT
    val restUri: URI get() = URI("https://$restHost:$restPort")
    val restUser: String get() = AdminPasswordUtil.adminUser
    val restPassword: String get() = System.getenv(restPasswordPropertyName) ?: AdminPasswordUtil.adminPassword
    val p2pHost: String get() = System.getenv(p2pHostPropertyName) ?: DEFAULT_P2P_HOST
    val p2pPort: Int get() = System.getenv(p2pPortPropertyName)?.toInt() ?: DEFAULT_P2P_PORT
    val p2pUri: URI get() = URI("https://$p2pHost:$p2pPort")
    val p2pProtocolVersion: String get() = "1"
}

object ClusterAConfig : ClusterConfig() {
    override val clusterId = "A"
}

object ClusterBConfig : ClusterConfig() {
    override val clusterId = "B"
}

object ClusterCConfig : ClusterConfig() {
    override val clusterId = "C"
}