package net.corda.ledger.common.impl.transaction.serializers

import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput

class PrivacySaltImplKryoSerializer : CheckpointInternalCustomSerializer<PrivacySaltImpl> {
    override fun write(output: CheckpointOutput, obj: PrivacySaltImpl) {
        output.writeBytesWithLength(obj.bytes)
    }

    override fun read(input: CheckpointInput, type: Class<PrivacySaltImpl>): PrivacySaltImpl {
        return PrivacySaltImpl(input.readBytesWithLength())
    }
}