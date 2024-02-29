package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.TransactionDependencyResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

@CordaSystemFlow
class ReceiveWireTransactionFlowV1(
    private val session: FlowSession
) : SubFlow<UtxoLedgerTransaction> {

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory

    @CordaInject
    lateinit var flowEngine: FlowEngine

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

        flowEngine.subFlow(
            TransactionDependencyResolutionFlow(
                session,
                wrappedUtxoWireTransaction.id,
                wrappedUtxoWireTransaction.notaryName,
                wrappedUtxoWireTransaction.dependencies,
                transactionPayload.filteredDependencies,
                wrappedUtxoWireTransaction.inputStateRefs,
                wrappedUtxoWireTransaction.referenceStateRefs
            )
        )

        session.send(Payload.Success(Unit))

        return utxoLedgerTransactionFactory.create(receivedTransaction)
    }
}
