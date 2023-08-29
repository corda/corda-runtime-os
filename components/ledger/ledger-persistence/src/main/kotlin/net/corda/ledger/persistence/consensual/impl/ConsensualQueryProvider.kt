package net.corda.ledger.persistence.consensual.impl

interface ConsensualQueryProvider {
    val findTransaction: String

    val findTransactionCpkChecksums: String

    val findTransactionSignatures: String

    val persistTransaction: String

    val persistTransactionComponentLeaf: String

    val persistTransactionStatus: String

    val persistTransactionSignature: String

    val persistTransactionCpk: String
}
