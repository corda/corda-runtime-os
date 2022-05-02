package net.corda.crypto.service

import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.data.crypto.wire.hsm.HSMConfig
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
    fun putHSMConfig(config: HSMConfig)

    /**
     * Returns list of all configured HSMs (except Soft HSM)
     */
    fun lookup(): List<HSMInfo>

    /**
     * Assigns an HSM out of existing pull of configured HSMs (except Soft HSM)
     */
    fun assignHSM(tenantId: String, category: String): HSMInfo

    /**
     * Assigns a Soft HSM, note that a new HSMConfig record will be created for each tenant.
     */
    fun assignSoftHSM(tenantId: String, category: String): HSMInfo

    /**
     * Returns information about assigned HSM.
     */
    fun findAssignedHSM(tenantId: String, category: String): HSMInfo?

    /**
     * Completely internal operation as it returns some sensitive information about the configuration. It's used
     * by the CryptoService factory to instantiate the corresponding services.
     */
    fun getPrivateTenantAssociation(tenantId: String, category: String): HSMTenantAssociation
}