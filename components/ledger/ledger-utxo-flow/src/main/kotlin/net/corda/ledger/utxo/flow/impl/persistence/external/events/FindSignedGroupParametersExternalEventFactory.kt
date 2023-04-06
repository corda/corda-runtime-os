package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock
import net.corda.data.ledger.persistence.FindSignedGroupParameters

@Component(service = [ExternalEventFactory::class])
class FindSignedGroupParametersExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<FindSignedGroupParametersParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindSignedGroupParametersParameters): Any {
        return FindSignedGroupParameters(parameters.hash)
    }
}

data class FindSignedGroupParametersParameters(val hash: String)