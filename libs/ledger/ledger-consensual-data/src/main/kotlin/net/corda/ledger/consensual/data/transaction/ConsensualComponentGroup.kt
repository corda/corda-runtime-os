package net.corda.ledger.consensual.data.transaction

/**
 * Specifies Consensual transaction component groups' enum.
 * For which each property corresponds to a transaction component group.
 * The position in the enum class declaration (ordinal) is used for component-leaf ordering when computing the
 * Merkle tree.
 *
 * @property METADATA The metadata parameters component group. Ordinal = 0. (It needs to be in the first position.)
 * @property TIMESTAMP The timestamp parameter component group. Ordinal = 1.
 * @property SIGNATORIES The required signing keys component group. Ordinal = 2.
 * @property OUTPUT_STATES The output states component group. Ordinal = 3.
 * @property OUTPUT_STATE_TYPES The output state types component group. Ordinal = 4.
 */

enum class ConsensualComponentGroup {
    METADATA, // needs to be in sync with
              // [net.corda.ledger.common.impl.transaction.WireTransactionImplKt.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID]
    TIMESTAMP,
    SIGNATORIES,
    OUTPUT_STATES,
    OUTPUT_STATE_TYPES
}