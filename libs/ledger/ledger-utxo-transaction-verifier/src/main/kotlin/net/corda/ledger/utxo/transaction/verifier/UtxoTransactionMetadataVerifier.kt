package net.corda.ledger.utxo.transaction.verifier

import net.corda.v5.ledger.common.transaction.TransactionMetadata

@Suppress("UNUSED_PARAMETER")
fun verifyMetadata(transactionMetadata: TransactionMetadata) {
    // TODO : CORE-7116 more verifications
    // TODO : CORE-7116 ? metadata verifications: nulls, order of CPKs, at least one CPK?)) Maybe from json schema?
}