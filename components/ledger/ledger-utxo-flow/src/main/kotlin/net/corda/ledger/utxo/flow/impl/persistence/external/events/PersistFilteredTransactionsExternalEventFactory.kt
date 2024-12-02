package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.crypto.core.bytes
import net.corda.data.crypto.SecureHash
import net.corda.data.ledger.persistence.PersistFilteredTransactionsAndSignatures
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.StateRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistFilteredTransactionsExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<PersistFilteredTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistFilteredTransactionParameters): Any {
        return PersistFilteredTransactionsAndSignatures
            .newBuilder()
            .setFilteredTransactionsAndSignatures(ByteBuffer.wrap(parameters.filteredTransactionsAndSignaturesMap))
            .setInputStateRefs(parameters.inputStateRefs.toAvro())
            .setReferenceStateRefs(parameters.referenceStateRefs.toAvro())
            .build()
    }

    private fun List<StateRef>.toAvro(): List<net.corda.data.ledger.utxo.StateRef> {
        return map { stateRef ->
            net.corda.data.ledger.utxo.StateRef(
                SecureHash(
                    stateRef.transactionId.algorithm,
                    ByteBuffer.wrap(stateRef.transactionId.bytes)
                ),
                stateRef.index
            )
        }
    }
}

@CordaSerializable
data class PersistFilteredTransactionParameters(
    val filteredTransactionsAndSignaturesMap: ByteArray,
    val inputStateRefs: List<StateRef>,
    val referenceStateRefs: List<StateRef>
)
