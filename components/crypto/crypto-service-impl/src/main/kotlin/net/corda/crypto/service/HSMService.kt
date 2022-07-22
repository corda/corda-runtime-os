package net.corda.crypto.service

import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.data.crypto.wire.hsm.AssociationInfo
import net.corda.lifecycle.Lifecycle

/**
 * Internal HSM service to support all HSM configuration and assignment operations.
 */
interface HSMService : Lifecycle {
    /**
     * Assigns an HSM out of existing pull of configured HSMs (except Soft HSM)
     */
    fun assignHSM(tenantId: String, category: String, context: Map<String, String>): AssociationInfo

    /**
     * Assigns a Soft HSM, note that a new HSMConfig record will be created for each tenant.
     */
    fun assignSoftHSM(tenantId: String, category: String, context: Map<String, String>): AssociationInfo

    /**
     * Returns information about assigned HSM by tenant and category.
     */
    fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation?

    /**
     * Returns information about assigned HSM by association id.
     */
    fun findAssociation(associationId: String): HSMTenantAssociation?
}