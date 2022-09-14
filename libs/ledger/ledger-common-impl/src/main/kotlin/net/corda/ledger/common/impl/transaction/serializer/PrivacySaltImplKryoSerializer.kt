package net.corda.ledger.common.impl.transaction.serializer

import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [CheckpointInternalCustomSerializer::class])
class PrivacySaltImplKryoSerializer @Activate constructor(): CheckpointInternalCustomSerializer<PrivacySaltImpl> {
    override val type: Class<PrivacySaltImpl> get() = PrivacySaltImpl::class.java

    override fun write(output: CheckpointOutput, obj: PrivacySaltImpl) {
        output.writeBytesWithLength(obj.bytes)
    }

    override fun read(input: CheckpointInput, type: Class<PrivacySaltImpl>): PrivacySaltImpl {
        return PrivacySaltImpl(input.readBytesWithLength())
    }
}