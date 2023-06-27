package net.corda.crypto.service

import net.corda.data.crypto.wire.hsm.HSMAssociationInfo

/**
 * Internal HSM service to support all HSM configuration and assignment operations.
 */
interface HSMService {
    /**
     * Assigns a Soft HSM, note that a new HSMConfig record will be created for each tenant.
     */
    fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo

    /**
     * Returns information about assigned HSM by tenant and category.
     */
    fun findAssignedHSM(tenantId: String, category: String): HSMAssociationInfo?
}