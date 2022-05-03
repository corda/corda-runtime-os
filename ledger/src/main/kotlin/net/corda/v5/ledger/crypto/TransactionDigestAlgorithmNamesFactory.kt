package net.corda.v5.ledger.crypto

interface TransactionDigestAlgorithmNamesFactory {
    fun create() : TransactionDigestAlgorithmNames
}
