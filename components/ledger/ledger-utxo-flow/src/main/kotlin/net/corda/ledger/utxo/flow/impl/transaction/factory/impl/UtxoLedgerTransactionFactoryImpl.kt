package net.corda.ledger.utxo.flow.impl.transaction.factory.impl

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.StateAndRef
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
    @Reference(service = UtxoLedgerStateQueryService::class)
    private val utxoLedgerStateQueryService: UtxoLedgerStateQueryService
) : UtxoLedgerTransactionFactory, UsedByFlow, SingletonSerializeAsToken {

    // fetch whole ledger tx as part of the find in the ledger service
    // put states into the cache
    // if all the states exist in the cache then no need to retrieve them from the database, need to hold in the checkpoint though or they
    // could get evicted by the time we come back from the database (this is an optimisation though so need to decide if it is worth
    // doing it).
    @Suspendable
    override fun create(
        wireTransaction: WireTransaction
    ): UtxoLedgerTransaction {
        val wrappedUtxoWireTransaction = WrappedUtxoWireTransaction(wireTransaction, serializationService)
        val allStateRefs =
            (wrappedUtxoWireTransaction.inputStateRefs + wrappedUtxoWireTransaction.referenceStateRefs)
                .distinct()

        val stateRefsToStateAndRefs =
            utxoLedgerStateQueryService.resolveStateRefs(allStateRefs).associateBy { it.ref }
        val inputStateAndRefs =
            wrappedUtxoWireTransaction.inputStateRefs.map {
                stateRefsToStateAndRefs[it]
                    ?: throw (CordaRuntimeException("Could not find StateRef $it when resolving input states."))
            }

        val referenceStateAndRefs =
            wrappedUtxoWireTransaction.referenceStateRefs.map {
                stateRefsToStateAndRefs[it]
                    ?: throw (CordaRuntimeException("Could not find StateRef $it when resolving reference states."))
            }

        return UtxoLedgerTransactionImpl(
            wrappedUtxoWireTransaction,
            inputStateAndRefs,
            referenceStateAndRefs
        )
    }

    // if any of the state refs are missing return a platform exception from the database worker
    // no point returning part of the data and then discarding it
    @Suspendable
    override fun create(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<StateAndRef<*>>, // or transaction output dtos
        referenceStateAndRefs: List<StateAndRef<*>> // or transaction output dtos
    ): UtxoLedgerTransaction {
        return UtxoLedgerTransactionImpl(
            WrappedUtxoWireTransaction(wireTransaction, serializationService),
            inputStateAndRefs,
            referenceStateAndRefs
        )
    }
}
