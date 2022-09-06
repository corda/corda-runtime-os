package net.corda.ledger.common.transaction.serialization

import net.corda.v5.ledger.common.transaction.PrivacySalt

/**
 * The class that actually gets serialized on the wire.
 */

data class WireTransactionContainerImpl(
    override val version: WireTransactionVersion,
    override val privacySalt: PrivacySalt,
    override val componentGroupLists: List<List<ByteArray>>,
) : WireTransactionContainer
