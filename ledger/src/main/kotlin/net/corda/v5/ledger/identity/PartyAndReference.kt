package net.corda.v5.ledger.identity

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes

/**
 * Reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the party.
 */
@CordaSerializable
data class PartyAndReference(val party: AbstractParty, val reference: OpaqueBytes) {
    override fun toString() = "$party$reference"
}

