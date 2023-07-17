package net.corda.crypto.service

import net.corda.data.crypto.wire.hsm.HSMAssociationInfo

/**
 * Internal service to track the settings that must be used for each tenant and category combination.
 *
 * Currently, the only important information that is managed via this interface is the choice of wrapping key used for
 * each tenant and category combination. Potentially one day we could store information about external key storage here.
 */

interface TenantInfoService {
    /**
     * Populate settings for a specific category on a specific tenant, and return those settings.
     *
     * @param tenantId the tenant (virtual node or special purpose cluster level
     * @param category indicates what the keys will be used for, and should be one of the constants
     *                 defined in CryptoConsts.Categories
     *
     * @return Information about crypto processing for a specific category of keys in a specific tenant.
     *         Includes `masterKeyAlias` which identifies the per-tenant master key used to wrap keys.
     */
    fun populate(tenantId: String, category: String): HSMAssociationInfo

    /**
     * Lookup settings for a specific category on a specific tenant.
     *
     * @param tenantId the tenant (virtual node or special purpose cluster level
     * @param category indicates what the keys will be used for, and should be one of the constants
     *                 defined in CryptoConsts.Categories
     *
     * @return Information about crypto processing for a specific category of keys in a specific tenant.
     *         Includes `masterKeyAlias` which identifies the per-tenant master key used to wrap keys.
     */

    fun lookup(tenantId: String, category: String): HSMAssociationInfo?
}