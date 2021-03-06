package net.corda.crypto.persistence

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.lifecycle.Lifecycle

/**
 * Persistence level actions to configure, assign, and query HSM configurations.
 */
interface HSMStore : Lifecycle {

    /**
     * Finds a tenant association with an HSM for the given category.
     */
    fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo?

    /**
     * Returns number of usages for each worker set.
     */
    fun getHSMUsage(): List<HSMUsage>

    /**
     * Associates a tenant with the configuration for the given category.
     */
    fun associate(
        tenantId: String,
        category: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMAssociationInfo
}