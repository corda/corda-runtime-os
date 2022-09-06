package net.corda.ledger.common.transaction.serialization

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.common.transaction.PrivacySalt

/**
 * Enumeration for WireTransaction version.
 */
@CordaSerializable
enum class WireTransactionVersion {
    VERSION_1
}

/**
 * Interface for WireTransaction wire data.
 * Typically, the child object actually gets serialized on the wire.
 * A WireTransactionContainer can be obtained from a WireTransaction.
 */
interface WireTransactionContainer {

    /**
     * Version of container.    //TODO
     */
    val version: WireTransactionVersion

    /**
     * Properties for wire transactions' serialisation.
     */
    val privacySalt: PrivacySalt
    val componentGroupLists: List<List<ByteArray>>
}
