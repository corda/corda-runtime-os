package net.corda.ledger.consensual.impl.transactions

internal enum class ConsensualComponentGroups {
    METADATA,
    TIMESTAMP,
    REQUIRED_SIGNERS,
    OUTPUT_STATES,
    OUTPUT_STATE_TYPES
}