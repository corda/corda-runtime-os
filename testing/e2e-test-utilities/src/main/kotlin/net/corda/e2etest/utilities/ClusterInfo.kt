package net.corda.e2etest.utilities

import net.corda.rest.annotations.RestApiVersion
import java.net.URI

/**
 * Information in relation to a target Corda cluster for the purposes of end-to-end testing.
 *
 * Implementations of this abstract class must specify an ID which aligns with the system properties set during the
 * E2E test run which define the target endpoints of a Corda cluster.
 * Optionally, [rest] and [p2p] properties can be overridden with specific values if system properties are not used.
 */
abstract class ClusterInfo {
    abstract val id: String

    private companion object {
        private const val DEFAULT_REST_HOST = "localhost"
        private const val DEFAULT_REST_PORT = 8888
        private const val DEFAULT_P2P_HOST = "localhost"
        private const val DEFAULT_P2P_PORT = 8080
        private val DEFAULT_REST_API_VERSION = RestApiVersion.C5_1.toString()
    }

    val name: String get() = "E2E_CLUSTER_${id}"
    private val restHostPropertyName get() = "${name}_REST_HOST"
    private val restPortPropertyName get() = "${name}_REST_PORT"
    private val restPasswordPropertyName get() = "${name}_REST_PASSWORD"
    private val p2pHostPropertyName get() = "${name}_P2P_HOST"
    private val p2pPortPropertyName get() = "${name}_P2P_PORT"
    private val restApiVersionPropertyName get() = "${name}_REST_API_VERSION"


    /**
     * REST API properties
     */
    open val rest by lazy {
        RestEndpointInfo(
            System.getenv(restHostPropertyName) ?: DEFAULT_REST_HOST,
            System.getenv(restPortPropertyName)?.toInt() ?: DEFAULT_REST_PORT,
            AdminPasswordUtil.adminUser,
            System.getenv(restPasswordPropertyName) ?: AdminPasswordUtil.adminPassword
        )
    }

    /**
     * P2P gateway properties.
     */
    open val p2p by lazy {
        P2PEndpointInfo(
            System.getenv(p2pHostPropertyName) ?: DEFAULT_P2P_HOST,
            System.getenv(p2pPortPropertyName)?.toInt() ?: DEFAULT_P2P_PORT,
            "1"
        )
    }

    open val restApiVersion by lazy {
        RestApiVersion.valueOf(System.getenv(restApiVersionPropertyName) ?: DEFAULT_REST_API_VERSION)
    }

}

/**
 * Data class for data relevant to the REST endpoint information of the E2E test cluster.
 */
data class RestEndpointInfo(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
) {
    val uri: URI = URI("https://$host:$port")
}

/**
 * Data class for data relevant to the P2P endpoint information of the E2E test cluster.
 */
data class P2PEndpointInfo(
    val host: String,
    val port: Int,
    val protocol: String
) {
    val uri: URI = URI("https://$host:$port")
}

/**
 * Default cluster info for E2E test cluster "A"
 */
object ClusterAInfo : ClusterInfo() {
    override val id = "A"
}

/**
 * Default cluster info for E2E test cluster "B"
 */
object ClusterBInfo : ClusterInfo() {
    override val id = "B"
}

/**
 * Default cluster info for E2E test cluster "C"
 */
object ClusterCInfo : ClusterInfo() {
    override val id = "C"
}

/**
 * Default cluster info for E2E test cluster on which a previous major version of deployment will run
 */
object ClusterDInfo : ClusterInfo() {
    override val id = "D"
}