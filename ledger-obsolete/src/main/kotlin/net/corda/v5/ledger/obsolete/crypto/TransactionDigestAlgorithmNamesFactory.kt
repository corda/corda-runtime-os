package net.corda.v5.ledger.obsolete.crypto

interface TransactionDigestAlgorithmNamesFactory {
    fun create() : TransactionDigestAlgorithmNames
}
