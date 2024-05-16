package net.corda.crypto.core

/**
 * Defines constants defining the cluster level tenant ids.
 * Cluster level tenants are those that can own an asymmetric key pairs.
 */
object CryptoTenants {
    /**
     * Tenant id of the P2P services.
     */
    const val P2P: String = "p2p"
}

/**
 * Defines identifier for a cluster crypto database and a helper function.
 */
object ClusterCryptoDb {
    const val CRYPTO_SCHEMA = "crypto"

    /**
     * Returns true if the given schema is referencing cluster Crypto database.
     */
    fun isReferencingClusterDb(schema: String) = schema == CRYPTO_SCHEMA || schema == CryptoTenants.P2P
}
