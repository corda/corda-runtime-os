package net.corda.applications.workers.rest.utils

abstract class E2eClusterConfig {
    abstract val restHostPropertyName: String
    abstract val restPortPropertyName: String
    abstract val restPasswordPropertyName: String
    abstract val p2pHostPropertyName: String
    abstract val p2pPortPropertyName: String

    private companion object {
        private const val DEFAULT_REST_HOST = "localhost"
        private const val DEFAULT_REST_PORT = 8888
        private const val DEFAULT_P2P_HOST = "localhost"
        private const val DEFAULT_P2P_PORT = 8080
    }

    val restHost: String get() = System.getenv(restHostPropertyName) ?: DEFAULT_REST_HOST
    val restPassword: String get() = System.getenv(restPasswordPropertyName) ?: AdminPasswordUtil.adminPassword
    val restPort: Int get() = System.getenv(restPortPropertyName)?.toInt() ?: DEFAULT_REST_PORT
    val p2pHost: String get() = System.getenv(p2pHostPropertyName) ?: DEFAULT_P2P_HOST
    val p2pPort: Int get() = System.getenv(p2pPortPropertyName)?.toInt() ?: DEFAULT_P2P_PORT
}

internal object E2eClusterAConfig : E2eClusterConfig() {
    override val restHostPropertyName = "E2E_CLUSTER_A_REST_HOST"
    override val restPortPropertyName = "E2E_CLUSTER_A_REST_PORT"
    override val restPasswordPropertyName = "E2E_CLUSTER_A_REST_PASSWORD"
    override val p2pHostPropertyName = "E2E_CLUSTER_A_P2P_HOST"
    override val p2pPortPropertyName = "E2E_CLUSTER_A_P2P_PORT"
}

internal object E2eClusterBConfig : E2eClusterConfig() {
    override val restHostPropertyName = "E2E_CLUSTER_B_REST_HOST"
    override val restPortPropertyName = "E2E_CLUSTER_B_REST_PORT"
    override val restPasswordPropertyName = "E2E_CLUSTER_B_REST_PASSWORD"
    override val p2pHostPropertyName = "E2E_CLUSTER_B_P2P_HOST"
    override val p2pPortPropertyName = "E2E_CLUSTER_B_P2P_PORT"
}

internal object E2eClusterCConfig : E2eClusterConfig() {
    override val restHostPropertyName = "E2E_CLUSTER_C_REST_HOST"
    override val restPortPropertyName = "E2E_CLUSTER_C_REST_PORT"
    override val restPasswordPropertyName = "E2E_CLUSTER_C_REST_PASSWORD"
    override val p2pHostPropertyName = "E2E_CLUSTER_C_P2P_HOST"
    override val p2pPortPropertyName = "E2E_CLUSTER_C_P2P_PORT"
}