package net.corda.crypto.persistence

interface CryptoRepositoryFactory {
    /**
     * Get access to crypto repository a specific tenant
     *
     * @param tenantId the ID to use (e.g. a virtual node holding ID, P2P or REST
     * @return an object for using the database
     */
    fun create(tenantId: String): CryptoRepository
}