package net.corda.ledger.consensual.flow.impl.transaction.verifier

import net.corda.v5.ledger.common.transaction.TransactionMetadata

@Suppress("UNUSED", "UNUSED_PARAMETER")
fun verifyMetadata(metadata: TransactionMetadata) {
    // TODO(CORE-5982 more verifications)
    // TODO(CORE-5982 ? metadata verifications: nulls, order of CPKs, at least one CPK?)) Maybe from json schema?
}