package net.corda.simulator

import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

interface SimulatedNode {


    /**
     * The holding identity for which this node was created.
     */
    val holdingIdentity: HoldingIdentity

    /**
     * The member for which this node was created.
     */
    val member : MemberX500Name

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
