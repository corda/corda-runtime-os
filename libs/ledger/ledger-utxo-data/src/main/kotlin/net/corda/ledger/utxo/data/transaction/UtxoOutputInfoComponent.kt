package net.corda.ledger.utxo.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.common.Party

@CordaSerializable
data class UtxoOutputInfoComponent(
    val encumbrance: Int?,
    val notary: Party, // TODO I kept it here, since we need something for the TransactionState anyway.
    val contractStateTag: String,
    val contractTag: String
)