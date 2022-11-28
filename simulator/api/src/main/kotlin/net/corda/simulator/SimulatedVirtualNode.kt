package net.corda.simulator

import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

/**
 * A simulated virtual node in which flows or instances of flows can be run.
 */
@DoNotImplement
interface SimulatedVirtualNode {

    /**
     * The holding identity for which this node was created.
     */
    val holdingIdentity: HoldingIdentity

    /**
     * The member for which this node was created.
     */
    val member : MemberX500Name

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @input The data to input to the flow.
     *
     * @return The response from the flow.
     */
    fun callFlow(input: RequestData): String

    /**
     * Calls the flow with the given request. Note that this call happens on the calling thread, which will wait until
     * the flow has completed before returning the response.
     *
     * @input The data to input to the flow.
     * @flow The flow to be called
     *
     * @return The response from the flow.
     */
    fun callInstanceFlow(input: RequestData, flow: RPCStartableFlow): String

    /**
     * @return The persistence service associated with this node.
     */
    fun getPersistenceService(): PersistenceService

    /**
     * Generates a key for this node which can then be accessed using the
     * [net.corda.v5.application.membership.MemberLookup] service and used with the
     * [net.corda.v5.application.crypto.SigningService] and
     * [net.corda.v5.application.crypto.DigitalSignatureVerificationService]. Note that Simulator does not actually
     * perform encryption, simulating it instead, and no private keys are held.
     *
     * @param alias An alias for the key.
     * @param hsmCategory The HSM category for the key.
     * @param scheme The scheme for the key. This is only used in verification. An ECDSA key will be returned.
     * @return An ECDSA public key.
     */
    fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey
}
