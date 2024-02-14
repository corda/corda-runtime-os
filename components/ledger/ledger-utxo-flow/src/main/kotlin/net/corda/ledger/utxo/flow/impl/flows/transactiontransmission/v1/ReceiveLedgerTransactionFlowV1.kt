package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.AbstractReceiveTransactionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

@CordaSystemFlow
class ReceiveLedgerTransactionFlowV1(
    session: FlowSession
) : AbstractReceiveTransactionFlow<UtxoLedgerTransaction>(session) {

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(): UtxoLedgerTransaction {
        @Suppress("unchecked_cast")
        val transactionPayload = session.receive(UtxoTransactionPayload::class.java)
            as UtxoTransactionPayload<WireTransaction>

        val receivedTransaction = transactionPayload.transaction

        requireNotNull(receivedTransaction) {
            "Didn't receive a transaction from counterparty."
        }

        val wrappedUtxoWireTransaction = WrappedUtxoWireTransaction(receivedTransaction, serializationService)

        performTransactionDependencyResolution(
            wrappedUtxoWireTransaction.id,
            wrappedUtxoWireTransaction.notaryName,
            wrappedUtxoWireTransaction.dependencies,
            transactionPayload.filteredDependencies
        )

        session.send(Payload.Success(Unit))

        return utxoLedgerTransactionFactory.create(receivedTransaction)
    }
}
