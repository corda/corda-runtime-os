package net.corda.ledger.utxo.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.common.Party

@CordaSerializable
data class UtxoOutputInfoComponent(
    val encumbrance: String?,
    val notary: Party,
    val contractStateTag: String,
    val contractTag: String
)