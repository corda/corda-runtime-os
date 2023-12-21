package net.corda.ledger.utxo.flow.impl.transaction.factory.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.membership.GroupParameters
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [UtxoLedgerTransactionFactory::class, UsedByFlow::class],
    scope = PROTOTYPE,
    property = [CORDA_SYSTEM_SERVICE],
)
class UtxoLedgerTransactionFactoryImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = UtxoLedgerStateQueryService::class)
    private val utxoLedgerStateQueryService: UtxoLedgerStateQueryService,
    @Reference(service = UtxoLedgerGroupParametersPersistenceService::class)
    private val utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService,
    @Reference(service = GroupParametersLookupInternal::class)
    private val groupParametersLookup: GroupParametersLookupInternal
) : UtxoLedgerTransactionFactory, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun create(
        wireTransaction: WireTransaction
    ): UtxoLedgerTransactionInternal {
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
            referenceStateAndRefs,
            getGroupParameters(wireTransaction)
        )
    }

    override fun create(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<UtxoVisibleTransactionOutputDto>,
        referenceStateAndRefs: List<UtxoVisibleTransactionOutputDto>
    ): UtxoLedgerTransactionInternal {
        return UtxoLedgerTransactionImpl(
            WrappedUtxoWireTransaction(wireTransaction, serializationService),
            inputStateAndRefs.map { it.toStateAndRef<ContractState>(serializationService) },
            referenceStateAndRefs.map { it.toStateAndRef<ContractState>(serializationService) },
            getGroupParameters(wireTransaction)
        )
    }

    override fun createWithStateAndRefs(
        wireTransaction: WireTransaction,
        inputStateAndRefs: List<StateAndRef<*>>,
        referenceStateAndRefs: List<StateAndRef<*>>
    ): UtxoLedgerTransactionInternal {
        return UtxoLedgerTransactionImpl(
            WrappedUtxoWireTransaction(wireTransaction, serializationService),
            inputStateAndRefs,
            referenceStateAndRefs,
            getGroupParameters(wireTransaction)
        )
    }

    private fun getGroupParameters(wireTransaction: WireTransaction): GroupParameters {
        val membershipGroupParametersHashString =
            requireNotNull((wireTransaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()) {
                "Membership group parameters hash cannot be found in the transaction metadata."
            }
        val currentGroupParameters = groupParametersLookup.currentGroupParameters
        val groupParameters =
            if (currentGroupParameters.hash.toString() == membershipGroupParametersHashString) {
                currentGroupParameters
            } else {
                val membershipGroupParametersHash = parseSecureHash(membershipGroupParametersHashString)
                utxoLedgerGroupParametersPersistenceService.find(membershipGroupParametersHash)
            }
        requireNotNull(groupParameters) {
            "Signed group parameters $membershipGroupParametersHashString related to the transaction " +
                "${wireTransaction.id} cannot be accessed."
        }
        return groupParameters
    }
}
