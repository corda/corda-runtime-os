package net.corda.ledger.common.transaction.serialization

import net.corda.v5.ledger.common.transaction.PrivacySalt

/**
 * The class that actually gets serialized on the wire.
 */

data class WireTransactionProxy(
    /**
     * Version of container.
     */
    val version: WireTransactionVersion,

    /**
     * Properties for wire transactions' serialisation.
     */
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>
)
