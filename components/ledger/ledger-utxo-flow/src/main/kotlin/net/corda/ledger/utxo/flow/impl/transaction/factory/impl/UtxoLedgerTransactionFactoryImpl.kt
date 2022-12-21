package net.corda.ledger.utxo.flow.impl.transaction.factory.impl

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [UtxoLedgerTransactionFactory::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE
)
class UtxoLedgerTransactionFactoryImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = UtxoLedgerPersistenceService::class)
    private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService
) : UtxoLedgerTransactionFactory, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun create(
        wireTransaction: WireTransaction
    ): UtxoLedgerTransaction {
        // todo optimize to load one tx once only
        val wrappedUtxoWireTransaction = WrappedUtxoWireTransaction(wireTransaction, serializationService)
        val inputStateAndRefs =
            wrappedUtxoWireTransaction.inputStateRefs.map { it ->
                utxoLedgerPersistenceService.find(it.transactionHash)?.outputStateAndRefs?.get(it.index)
                    ?: throw (CordaRuntimeException("Input state not found ${it.transactionHash} ${it.index}"))
            }

        val referenceInputStateAndRefs =
            wrappedUtxoWireTransaction.referenceInputStateRefs.map { it ->
                utxoLedgerPersistenceService.find(it.transactionHash)?.outputStateAndRefs?.get(it.index)
                    ?: throw (CordaRuntimeException("Reference input state not found ${it.transactionHash} ${it.index}"))
            }

        return UtxoLedgerTransactionImpl(
            wrappedUtxoWireTransaction,
            inputStateAndRefs,
            referenceInputStateAndRefs
        )
    }
}
