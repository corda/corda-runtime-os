package net.corda.ledger.utxo.flow.impl

import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransactionVerifier
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [ UtxoLedgerService::class, UsedByFlow::class ], scope = PROTOTYPE)
class UtxoLedgerServiceImpl @Activate constructor(
    @Reference(service = UtxoSignedTransactionFactory::class)
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory
) : UtxoLedgerService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun getTransactionBuilder(): UtxoTransactionBuilder =
        UtxoTransactionBuilderImpl(utxoSignedTransactionFactory)

    override fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>> {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T> {
        TODO("Not yet implemented")
    }

    override fun findSignedTransaction(id: SecureHash): UtxoSignedTransaction? {
        TODO("Not yet implemented")
    }

    override fun findLedgerTransaction(id: SecureHash): UtxoLedgerTransaction? {
        TODO("Not yet implemented")
    }

    override fun finality(
        signedTransaction: UtxoSignedTransaction,
        sessions: List<FlowSession>
    ): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }

    override fun receiveFinality(session: FlowSession, verifier: UtxoSignedTransactionVerifier): UtxoSignedTransaction {
        TODO("Not yet implemented")
    }
}
