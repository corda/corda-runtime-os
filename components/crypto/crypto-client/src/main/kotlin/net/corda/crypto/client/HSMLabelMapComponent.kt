package net.corda.crypto.client

import net.corda.lifecycle.Lifecycle

/**
 * Provides a map of HSM configuration labels to help to route messages for HSMs which have dedicated workers.
 */
interface HSMLabelMapComponent : Lifecycle {
    /**
     * Returns a label which is associated with the HSM configured for specified tenant and category.
     */
    fun get(tenantId: String, category: String)
}