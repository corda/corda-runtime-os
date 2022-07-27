package net.corda.ledger.consensual.impl.transactions

internal enum class ConsensualComponentGroups {
    METADATA,
    TIMESTAMP,
    REQUIRED_SIGNING_KEYS, //TODO(signers? signing keys? CORE-5936)
    OUTPUT_STATES,
    OUTPUT_STATE_TYPES
}