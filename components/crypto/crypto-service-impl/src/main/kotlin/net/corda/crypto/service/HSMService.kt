package net.corda.crypto.service

import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.lifecycle.Lifecycle

/**
 * Internal HSM service to support all HSM configuration and assignment operations.
 */
interface HSMService : Lifecycle {
    /**
     * Assigns an HSM out of existing pull of configured HSMs (except Soft HSM)
     */
    fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMAssociationInfo

    /**
     * Assigns a Soft HSM, note that a new HSMConfig record will be created for each tenant.
     */
    fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo

    /**
     * Returns information about assigned HSM by tenant and category.
     */
    fun findAssignedHSM(tenantId: String, category: String): HSMAssociationInfo?
}