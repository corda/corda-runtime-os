package net.corda.simulator

import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

interface SimulatedVirtualNode {
    val holdingIdentity: HoldingIdentity
    val member : MemberX500Name

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @input the data to input to the flow
     *
     * @return the response from the flow
     */
    fun callFlow(input: RequestData): String

    /**
     * Retrieves the persistence service associated with this node's member
     */
    fun getPersistenceService(): PersistenceService

    /**
     * Generates a key for this node with the given alias, HSM category and scheme.
     */
    fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey
}
