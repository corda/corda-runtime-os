package net.corda.crypto.core

/**
 * Defines constants defining the cluster level tenant ids and some helper functions.
 */
object CryptoTenants {

    /**
     * Tenant id used by the crypto services in some cases to attribute the ownership of the operation however
     * that tenant does not own any asymmetric key pairs, that's why it's not included in the [allClusterTenants] variable.
     */
    const val CRYPTO: String = "crypto"

    /**
     * Tenant id of the P2P services.
     */
    const val P2P: String = "p2p"

    /**
     * Tenant id of the REST
     */
    const val REST: String = "rest"

    /**
     * Lists all cluster level tenants which can own asymmetric key pairs
     */
    val allClusterTenants: Set<String> = setOf(
        P2P,
        REST
    )

    /**
     * Returns true if the tenant is one of the cluster's.
     */
    fun isClusterTenant(tenantId: String) =
        tenantId == CRYPTO || allClusterTenants.contains(tenantId)
}