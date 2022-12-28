package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.ledger.common.transaction.TransactionMetadata

class UtxoTransactionMetadataVerifier(private val transactionMetadata: TransactionMetadata) {

    fun verify() {
        // TODO : CORE-7116 more verifications
        // TODO : CORE-7116 ? metadata verifications: nulls, order of CPKs, at least one CPK?)) Maybe from json schema?
    }
}