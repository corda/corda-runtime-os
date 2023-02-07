package net.corda.applications.workers.rpc.utils

abstract class E2eClusterConfig {
    abstract val rpcHostPropertyName: String
    abstract val rpcPortPropertyName: String
    abstract val rpcPasswordPropertyName: String
    abstract val p2pHostPropertyName: String
    abstract val p2pPortPropertyName: String

    private companion object {
        private const val DEFAULT_RPC_HOST = "localhost"
        private const val DEFAULT_RPC_PORT = 8888
        private const val DEFAULT_P2P_HOST = "localhost"
        private const val DEFAULT_P2P_PORT = 8080
    }

    val rpcHost: String get() = System.getenv(rpcHostPropertyName) ?: DEFAULT_RPC_HOST
    val rpcPassword: String get() = System.getenv(rpcPasswordPropertyName) ?: AdminPasswordUtil.adminPassword
    val rpcPort: Int get() = System.getenv(rpcPortPropertyName)?.toInt() ?: DEFAULT_RPC_PORT
    val p2pHost: String get() = System.getenv(p2pHostPropertyName) ?: DEFAULT_P2P_HOST
    val p2pPort: Int get() = System.getenv(p2pPortPropertyName)?.toInt() ?: DEFAULT_P2P_PORT
}

internal object E2eClusterAConfig : E2eClusterConfig() {
    override val rpcHostPropertyName = "E2E_CLUSTER_A_RPC_HOST"
    override val rpcPortPropertyName = "E2E_CLUSTER_A_RPC_PORT"
    override val rpcPasswordPropertyName = "E2E_CLUSTER_A_RPC_PASSWORD"
    override val p2pHostPropertyName = "E2E_CLUSTER_A_P2P_HOST"
    override val p2pPortPropertyName = "E2E_CLUSTER_A_P2P_PORT"
}

internal object E2eClusterBConfig : E2eClusterConfig() {
    override val rpcHostPropertyName = "E2E_CLUSTER_B_RPC_HOST"
    override val rpcPortPropertyName = "E2E_CLUSTER_B_RPC_PORT"
    override val rpcPasswordPropertyName = "E2E_CLUSTER_B_RPC_PASSWORD"
    override val p2pHostPropertyName = "E2E_CLUSTER_B_P2P_HOST"
    override val p2pPortPropertyName = "E2E_CLUSTER_B_P2P_PORT"
}

internal object E2eClusterCConfig : E2eClusterConfig() {
    override val rpcHostPropertyName = "E2E_CLUSTER_C_RPC_HOST"
    override val rpcPortPropertyName = "E2E_CLUSTER_C_RPC_PORT"
    override val rpcPasswordPropertyName = "E2E_CLUSTER_C_RPC_PASSWORD"
    override val p2pHostPropertyName = "E2E_CLUSTER_C_P2P_HOST"
    override val p2pPortPropertyName = "E2E_CLUSTER_C_P2P_PORT"
}