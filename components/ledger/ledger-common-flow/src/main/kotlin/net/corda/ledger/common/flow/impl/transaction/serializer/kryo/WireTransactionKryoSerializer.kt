package net.corda.ledger.common.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ CheckpointInternalCustomSerializer::class, UsedByFlow::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class WireTransactionKryoSerializer @Activate constructor(
    @Reference(service = WireTransactionFactory::class) private val wireTransactionFactory: WireTransactionFactory
) : CheckpointInternalCustomSerializer<WireTransaction>, UsedByFlow {
    override val type = WireTransaction::class.java

    override fun write(output: CheckpointOutput, obj: WireTransaction) {
        output.writeClassAndObject(obj.privacySalt)
        output.writeClassAndObject(obj.componentGroupLists)
    }

    override fun read(input: CheckpointInput, type: Class<WireTransaction>): WireTransaction {
        val privacySalt = input.readClassAndObject() as PrivacySalt
        val componentGroupLists : List<List<ByteArray>> = uncheckedCast(input.readClassAndObject())
        return wireTransactionFactory.create(
            componentGroupLists,
            privacySalt,
        )
    }
}

