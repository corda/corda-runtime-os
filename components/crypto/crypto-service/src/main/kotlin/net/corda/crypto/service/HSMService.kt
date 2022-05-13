package net.corda.crypto.service

import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.Lifecycle

/**
 * Internal HSM service to support all HSM configuration and assignment operations.
 */
interface HSMService : Lifecycle {
    /**
     * Persists a new HSM configuration.
     * If the id is specified then this operation is treated as an update and the configuration must exist.
     */
    fun putHSMConfig(info: HSMInfo, serviceConfig: ByteArray): String

    /**
     * Assigns an HSM out of existing pull of configured HSMs (except Soft HSM)
     */
    fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo

    /**
     * Assigns a Soft HSM, note that a new HSMConfig record will be created for each tenant.
     */
    fun assignSoftHSM(tenantId: String, category: String): HSMInfo

    /**
     * Replaces or adds the category links to HSM configuration.
     */
    fun linkCategories(configId: String, links: List<HSMCategoryInfo>)

    /**
     * Return list of linked categories for the given HSM configuration.
     */
    fun getLinkedCategories(configId: String): List<HSMCategoryInfo>

    /**
     * Returns list of all configured HSMs.
     *
     * @param filter the optional map of the filter parameters such as
     * serviceName.*
     */
    fun lookup(filter: Map<String, String>): List<HSMInfo>

    /**
     * Returns information about assigned HSM by tenant and category.
     */
    fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation?

    /**
     * Returns information about assigned HSM by association id.
     */
    fun findAssociation(associationId: String): HSMTenantAssociation?

    /**
     * Returns information about an HSM. It's used by the CryptoService factory.
     */
    fun findHSMConfig(configId: String): HSMConfig?
}