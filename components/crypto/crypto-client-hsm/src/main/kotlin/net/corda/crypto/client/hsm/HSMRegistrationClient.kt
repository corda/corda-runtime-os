package net.corda.crypto.client.hsm

import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.lifecycle.Lifecycle

/**
 * The HSM registration client to generate keys.
 */
interface HSMRegistrationClient : Lifecycle {
    /**
     * Assigns hardware backed HSM to the given tenant.
     *
     * @param tenantId the tenant which HSM should be assigned to.
     * @param category which category the assignment should be done.
     * @param context optional operation context, which may advise on how the assignment should be done.
     *
     * @return info object about the assigned HSM.
     *
     * @throws IllegalArgumentException if the category is not supported by any
     */
    fun assignHSM(
        tenantId: String,
        category: String,
        context: Map<String, String>
    ): HSMAssociationInfo

    /**
     * Assigns soft HSM to the given tenant.
     *
     * @param tenantId the tenant which HSM should be assigned to.
     * @param category which category the assignment should be done.
     * @param context optional operation context, which may advise on how the assignment should be done.
     * *
     * @return info object about the assigned HSM.
     */
    fun assignSoftHSM(
        tenantId: String,
        category: String,
        context: Map<String, String>
    ): HSMAssociationInfo

    /**
     * Looks up information about the assigned HSM.
     *
     * @return The HSM's info if it's assigned otherwise null.
     */
    fun findHSM(tenantId: String, category: String) : HSMAssociationInfo?
}