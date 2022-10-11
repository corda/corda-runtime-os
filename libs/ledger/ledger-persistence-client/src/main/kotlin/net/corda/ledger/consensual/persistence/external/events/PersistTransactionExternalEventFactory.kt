package net.corda.ledger.consensual.persistence.external.events

import net.corda.data.ledger.consensual.PersistTransaction
import java.nio.ByteBuffer
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistTransactionExternalEventFactory : AbstractLedgerExternalEventFactory<PersistTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistTransactionParameters): Any {
        return PersistTransaction(parameters.transaction)
    }
}

data class PersistTransactionParameters(val transaction: ByteBuffer)