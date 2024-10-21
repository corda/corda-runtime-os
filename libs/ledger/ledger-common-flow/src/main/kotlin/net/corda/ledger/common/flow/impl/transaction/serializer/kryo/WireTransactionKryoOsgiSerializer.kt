package net.corda.ledger.common.flow.impl.transaction.serializer.kryo

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.libs.common.flow.impl.transaction.kryo.WireTransactionKryoSerializer
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [CheckpointInternalCustomSerializer::class, UsedByFlow::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class WireTransactionKryoOsgiSerializer(
    delegate: WireTransactionKryoSerializer
) : CheckpointInternalCustomSerializer<WireTransaction> by delegate, UsedByFlow {

    @Activate
    constructor(
        @Reference(service = WireTransactionFactory::class)
        wireTransactionFactory: WireTransactionFactory
    ) : this(WireTransactionKryoSerializer(wireTransactionFactory))
}
