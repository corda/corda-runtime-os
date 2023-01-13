package net.corda.ledger.consensual.flow.impl.transaction.verifier

import net.corda.v5.ledger.common.transaction.TransactionMetadata

class ConsensualTransactionMetadataVerifier(private val metadata: TransactionMetadata) {

    fun verify() {
        // TODO(CORE-5982 more verifications)
        // TODO(CORE-5982 ? metadata verifications: nulls, order of CPKs, at least one CPK?)) Maybe from json schema?
    }
}