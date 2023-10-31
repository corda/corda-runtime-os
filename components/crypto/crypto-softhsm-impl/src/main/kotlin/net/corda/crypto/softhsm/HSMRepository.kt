package net.corda.crypto.softhsm

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.persistence.HSMUsage
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import java.io.Closeable

interface HSMRepository : Closeable {

    /**
     * Finds a tenant association with an HSM for the given category.
     *
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
        masterKeyPolicy: MasterKeyPolicy,
    ): HSMAssociationInfo

    /**
     * Returns existing association, otherwise creates association with specified masterKeyPolicy
     *
     * @param tenantId The tenant ID to consider
     * @param category The category to consider (acts like a string enum)
     * @param masterKeyPolicy The master key policy to set if the association does not exist
     */
    fun createOrLookupCategoryAssociation(
        tenantId: String,
        category: String,
        masterKeyPolicy: MasterKeyPolicy,
    ): HSMAssociationInfo

}