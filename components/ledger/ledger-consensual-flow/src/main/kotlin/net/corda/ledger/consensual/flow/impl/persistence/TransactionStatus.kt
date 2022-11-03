package net.corda.ledger.consensual.flow.impl.persistence

enum class TransactionStatus(val value: String) {
    UNVERIFIED("U"),
    VERIFIED("V")
}