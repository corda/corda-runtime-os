package net.corda.crypto.persistence.hsm

import net.corda.lifecycle.Lifecycle

/**
 * Persistence level actions to configure, assign, and query HSM configurations.
 */
interface HSMStore : Lifecycle {

    /**
     * Finds a tenant association with an HSM for the given category.
     */
    fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation?

    /**
     * Finds a tenant association with an HSM by the given id.
     */
    fun findTenantAssociation(associationId: String): HSMTenantAssociation?

    /**
     * Returns number of usages for each worker set.
     */
    fun getHSMUsage(): List<HSMUsage>

    /**
     * Associates a tenant with the configuration for the given category.
     */
    fun associate(tenantId: String, category: String, workerSetId: String): HSMTenantAssociation
}