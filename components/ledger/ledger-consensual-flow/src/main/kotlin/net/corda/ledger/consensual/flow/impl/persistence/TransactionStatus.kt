package net.corda.ledger.consensual.flow.impl.persistence

enum class TransactionStatus(val value: String) {
    INVALID("I"),
    UNVERIFIED("U"),
    VERIFIED("V")
}