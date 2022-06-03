package net.corda.crypto.persistence.hsm

import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo

/**
 * Persistence level actions to configure, assign, and query HSM configurations.
 */
interface HSMStoreActions : AutoCloseable {

    /**
     * Find HSM configuration by its id.
     */
    fun findConfig(configId: String): HSMConfig?

    /**
     * Finds a tenant association with an HSM for the given category.
     */
    fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation?

    /**
     * Finds a tenant association with an HSM by the given id.
     */
    fun findTenantAssociation(associationId: String): HSMTenantAssociation?

    /**
     * Returns all configured HSMs.
     */
    fun lookup(filter: Map<String, String>): List<HSMInfo>

    /**
     * Returns allocation stats for HSM linked to the specified category, note that the stats reflect allocations
     * regardless of the category.
     */
    fun getHSMStats(category: String): List<HSMStat>

    /**
     * Return list of linked categories for the given HSM configuration.
     */
    fun getLinkedCategories(configId: String): List<HSMCategoryInfo>

    /**
     * Replaces or adds the category links to HSM configuration.
     */
    fun linkCategories(configId: String, links: List<HSMCategoryInfo>)

    /**
     * Adds a new HSM configuration.
     *
     * @return the id of the new configuration.
     */
    fun add(info: HSMInfo, serviceConfig: ByteArray): String

    /**
     * Merges an HSM configuration with the existing.
     */
    fun merge(info: HSMInfo, serviceConfig: ByteArray)

    /**
     * Associates a tenant with the configuration for the given category.
     */
    fun associate(tenantId: String, category: String, configId: String): HSMTenantAssociation
}