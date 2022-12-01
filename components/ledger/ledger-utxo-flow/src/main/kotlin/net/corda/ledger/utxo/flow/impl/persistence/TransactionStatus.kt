package net.corda.ledger.utxo.flow.impl.persistence

enum class TransactionStatus(val value: String) {
    INVALID("I"),
    UNVERIFIED("U"),
    VERIFIED("V")
}