package net.corda.ledger.utxo.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

@CordaSerializable
data class UtxoOutputInfoComponent(
    val encumbrance: String?,
    val encumbranceGroupSize: Int?,
    val notaryName: MemberX500Name,
    val notaryKey: PublicKey,
    val contractStateTag: String,
    val contractTag: String
)
