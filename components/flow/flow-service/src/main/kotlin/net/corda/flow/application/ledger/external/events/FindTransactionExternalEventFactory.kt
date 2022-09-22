package net.corda.flow.application.ledger.external.events

import net.corda.data.ledger.consensual.FindTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindTransactionExternalEventFactory : AbstractLedgerExternalEventFactory<FindTransactionParameters> {
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindTransactionParameters): Any {
        return FindTransaction(parameters.id)
    }
}

data class FindTransactionParameters(val id: String)