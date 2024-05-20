package net.corda.crypto.core

/**
 * Defines constants defining the cluster level tenant ids and a helper function.
 * Cluster level tenants are those that can own an asymmetric key pairs.
 */
object CryptoTenants {
    /**
     * Tenant id of the P2P services.
     */
    const val P2P: String = "p2p"

    /**
     * Lists all cluster level tenants.
     */
    val allClusterTenants: Set<String> = setOf(
        P2P,
    )
}

/**
 * Defines identifier for a cluster crypto database and a helper function.
 */
object ClusterCryptoDb {
    const val SCHEMA_NAME = "crypto"

    /**
     * Returns true if the given schema is referencing cluster Crypto database.
     */
    fun isReferencingClusterDb(schema: String) =
        schema == SCHEMA_NAME || CryptoTenants.allClusterTenants.contains(schema)
}
