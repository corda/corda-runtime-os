package net.corda.crypto

/**
 * Provides a map of HSM configuration labels to help to route messages for HSMs which have dedicated workers.
 */
interface HSMLabelMap {
    /**
     * Returns a label which is associated with the HSM configured for specified tenant and category.
     */
    fun get(tenantId: String, category: String): String
}