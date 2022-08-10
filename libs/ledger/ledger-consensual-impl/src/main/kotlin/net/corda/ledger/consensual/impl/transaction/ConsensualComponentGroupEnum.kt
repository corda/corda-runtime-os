package net.corda.ledger.consensual.impl.transaction

internal enum class ConsensualComponentGroupEnum {
    METADATA, // needs to be in sync with [net.corda.ledger.common.impl.transaction.WireTransactionImplKt.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID]
    TIMESTAMP,
    REQUIRED_SIGNING_KEYS, //TODO(signers? signing keys? CORE-5936)
    OUTPUT_STATES,
    OUTPUT_STATE_TYPES
}