package net.corda.ledger.utxo.impl.transaction

data class UtxoOutputInfoComponent(
    val encumbrance: Int?,
    val contractStateTag: String,
    val contractTag: String
)