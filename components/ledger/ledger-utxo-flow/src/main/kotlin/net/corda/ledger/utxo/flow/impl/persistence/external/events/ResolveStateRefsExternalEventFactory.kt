package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.crypto.SecureHash
import net.corda.data.ledger.persistence.ResolveStateRefs
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.ledger.utxo.StateRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class ResolveStateRefsExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<ResolveStateRefsParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: ResolveStateRefsParameters): Any {
        return ResolveStateRefs(parameters.stateRefs.map {
            net.corda.data.ledger.utxo.StateRef(
                SecureHash(
                    it.transactionHash.algorithm,
                    ByteBuffer.wrap(it.transactionHash.bytes)
                ), it.index
            )
        })
    }
}

data class ResolveStateRefsParameters(
    val stateRefs: List<StateRef>
)