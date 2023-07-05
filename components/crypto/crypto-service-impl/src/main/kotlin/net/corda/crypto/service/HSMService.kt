package net.corda.crypto.service

import net.corda.data.crypto.wire.hsm.HSMAssociationInfo

/**
 * Internal service to handle configuration per tenant. We used to envision that different tenants would
 * use different HSMs, and so tenant configuration would be associated with HSMs, 
 * which is why this is called HSMService. CORE-15266 concerns renaming this.
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