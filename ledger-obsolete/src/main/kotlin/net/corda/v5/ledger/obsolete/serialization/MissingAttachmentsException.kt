package net.corda.v5.ledger.obsolete.serialization

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash

/** Thrown during deserialization to indicate that an attachment needed to construct the [WireTransaction][net.corda.v5.ledger.obsolete.transactions.WireTransaction] is not found. */
@CordaSerializable
class MissingAttachmentsException(val ids: List<SecureHash>, message: String?) : CordaRuntimeException(message) {

    constructor(ids: List<SecureHash>) : this(ids, null)
}