package net.corda.crypto.softhsm

import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo

/**
 * Internal service to track the settings that must be used for each tenant and category combination that calls
 * into the crypto service.
 *
 * Currently, the only information that is managed via this interface is the choice of wrapping key used for each tenant and
 * category combination. Potentially one day we could store information about external key storage here.
 */

interface TenantInfoService {
    /**
     * Populate settings for a specific category on a specific tenant, and return those settings.
     * 
     * @param tenantId the tenant (virtual node or special purpose cluster-level tenant)
     * @param category indicates what the keys will be used for, and should be one of the constants 
     *                 defined in CryptoConsts.Categories
     * @param cryptoService a reference to the crypto service, which is used to allocate a wrapping key
     *                      if necessary. This is passed in as a parameter rather than injected in the constructor
     *                      so as to avoid a circular dependency at construction time, since TenantInfoService
     *                      and CryptoService call each other.
     * 
     * @return Information about crypto processing for a specific category of keys in a specific tenant.
     *         Includes `masterKeyAlias` which identifies the per-tenant master key used to wrap keys.
     */
    fun populate(tenantId: String, category: String, cryptoService: CryptoService): HSMAssociationInfo

    /**
     * Lookup settings assigned HSM by tenant and category.
     */
    fun lookup(tenantId: String, category: String): HSMAssociationInfo?
}