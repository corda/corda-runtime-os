package net.corda.crypto.service

import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
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
    fun putHSMConfig(config: HSMConfig): String

    /**
     * Returns list of all configured HSMs (except Soft HSM)
     */
    fun lookup(filter: Map<String, String> = emptyMap()): List<HSMInfo>

    /**
     * Assigns an HSM out of existing pull of configured HSMs (except Soft HSM)
     */
    fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo

    /**
     * Assigns a Soft HSM, note that a new HSMConfig record will be created for each tenant.
     */
    fun assignSoftHSM(tenantId: String, category: String): HSMInfo

    /**
     * Returns information about assigned HSM.
     */
    fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation?

    /**
     * Returns information about an HSM. It's used by the CryptoService factory.
     */
    fun findHSMConfig(configId: String): HSMConfig?
}