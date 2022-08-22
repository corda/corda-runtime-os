package net.corda.ledger.consensual.impl.transaction

/**
 * Specifies Consensual transaction component groups.
 *
 * @property METADATA The metadata parameters component group. Ordinal = 0. (It needs to be in the first position.)
 * @property TIMESTAMP The timestamp parameter component group. Ordinal = 1.
 * @property REQUIRED_SIGNING_KEYS The required signing keys component group. Ordinal = 2.
 * @property OUTPUT_STATES The output states component group. Ordinal = 3.
 * @property OUTPUT_STATE_TYPES The output state types component group. Ordinal = 4.
 */

internal enum class ConsensualComponentGroupEnum {
    METADATA, // needs to be in sync with [net.corda.ledger.common.impl.transaction.WireTransactionImplKt.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID]
    TIMESTAMP,
    REQUIRED_SIGNING_KEYS,
    OUTPUT_STATES,
    OUTPUT_STATE_TYPES
}