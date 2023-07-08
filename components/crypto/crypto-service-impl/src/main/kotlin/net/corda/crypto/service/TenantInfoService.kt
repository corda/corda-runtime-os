package net.corda.crypto.service

import net.corda.data.crypto.wire.hsm.HSMAssociationInfo

/**
 * Internal service to track the settings that must be used for each tenant and category combination.
 *
 * Currently, the only information that is managed via this interface is the choice of wrapping key used for each tenant and
 * category combination. Potentially one day we could store information about external key storage here.
 */

interface TenantInfoService {
    /**
     * Populate settings for a specific category on a specific tenant  
     * 
     * Result type is HSMAssociationInfo, which is a type defined in the public API and therefore
     * we did not rename it to TenantInfo since that would break compatibility.
     */
    fun populate(tenantId: String, category: String): HSMAssociationInfo

    /**
     * Lookup settings assigned HSM by tenant and category.
     */
    fun lookup(tenantId: String, category: String): HSMAssociationInfo?
}