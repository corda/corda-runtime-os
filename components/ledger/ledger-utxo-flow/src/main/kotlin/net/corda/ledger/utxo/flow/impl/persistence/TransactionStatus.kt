package net.corda.ledger.utxo.flow.impl.persistence

enum class TransactionStatus(val value: String) {
    UNVERIFIED("U"),
    VERIFIED("V")
}