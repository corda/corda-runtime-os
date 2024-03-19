package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.IncrementTransactionRepairAttemptCount
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class IncrementTransactionRepairAttemptCountExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<IncrementTransactionRepairAttemptCountParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: IncrementTransactionRepairAttemptCountParameters): Any {
        return IncrementTransactionRepairAttemptCount.newBuilder()
            .setId(parameters.id.toString())
            .build()
    }
}

@CordaSerializable
data class IncrementTransactionRepairAttemptCountParameters(val id: SecureHash)
