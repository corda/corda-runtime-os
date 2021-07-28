package net.corda.serializers

import net.corda.kryoserialization.CheckpointInput
import net.corda.kryoserialization.CheckpointInternalCustomSerializer
import net.corda.kryoserialization.CheckpointOutput
import net.corda.v5.ledger.transactions.PrivacySalt

/*
 * Avoid deserialising PrivacySalt via its default constructor
 * because the random number generator may not be available.
 */
class PrivacySaltSerializer : CheckpointInternalCustomSerializer<PrivacySalt> {
    override fun write(output: CheckpointOutput, obj: PrivacySalt) {
        output.writeBytesWithLength(obj.bytes)
    }

    override fun read(input: CheckpointInput, type: Class<PrivacySalt>): PrivacySalt {
        return PrivacySalt(input.readBytesWithLength())
    }
}