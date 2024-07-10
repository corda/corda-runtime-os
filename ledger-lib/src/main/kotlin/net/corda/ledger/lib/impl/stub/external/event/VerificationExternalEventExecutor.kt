package net.corda.ledger.lib.impl.stub.external.event

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.utxo.data.transaction.TransactionVerificationResult
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationParameters
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService

class VerificationExternalEventExecutor(
    private val serializationService: SerializationService
) : ExternalEventExecutor {

    @Suppress("unchecked_cast")
    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        // TODO ADD ACTUAL VERIFICATION LOGIC
        
        //val params = parameters as TransactionVerificationParameters


        /*val transactionFactory = { serializationService.deserialize<UtxoLedgerTransactionContainer>(params.transaction).run {
            UtxoLedgerTransactionImpl(
                WrappedUtxoWireTransaction(wireTransaction, serializationService),
                inputStateAndRefs,
                referenceStateAndRefs,
                groupParameters
            )
        } }*/
        //val transaction = transactionFactory.invoke()

        //println(transaction)
        return TransactionVerificationResult(TransactionVerificationStatus.VERIFIED) as RESUME
    }
}