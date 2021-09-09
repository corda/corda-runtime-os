package net.corda.v5.ledger

import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.CordaSerializable

/**
 * Stores information about notaries available in the network.
 */
@CordaSerializable
interface NotaryInfo {
    companion object {
        // Type for validating notary.
        const val NOTARY_TYPE_VALIDATING = "corda.notary.type.validating"
        // Type for non validating notary.
        const val NOTARY_TYPE_NON_VALIDATING = "corda.notary.type.non-validating"
    }

    /**
     * Identity of the notary (note that it can be an identity of the distributed node).
     */
    val party: Party

    /**
     * The type of the notary.
     */
    val type: String
}