package net.corda.ledger.common.transaction.serialization

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Enumeration for WireTransaction version.
 */
@CordaSerializable
enum class WireTransactionVersion {
    VERSION_1
}