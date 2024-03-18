package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.IncrementTransactionRecoveryAttemptCount
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class IncrementTransactionRecoveryAttemptCountExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<IncrementTransactionRecoveryAttemptCountParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: IncrementTransactionRecoveryAttemptCountParameters): Any {
        return IncrementTransactionRecoveryAttemptCount.newBuilder()
            .setId(parameters.id.toString())
            .build()
    }
}

@CordaSerializable
data class IncrementTransactionRecoveryAttemptCountParameters(val id: SecureHash)
