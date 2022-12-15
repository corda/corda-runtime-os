package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindUnconsumedStatesByTypeParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionIfDoesNotExistExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionIfDoesNotExistParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.UpdateTransactionStatusExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.UpdateTransactionStatusParameters
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.nio.ByteBuffer

@Component(
    service = [ UtxoLedgerPersistenceService::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
class UtxoLedgerPersistenceServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = UtxoSignedTransactionFactory::class)
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory
) : UtxoLedgerPersistenceService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun find(id: SecureHash, transactionStatus: TransactionStatus): UtxoSignedTransaction? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindTransactionExternalEventFactory::class.java,
                FindTransactionParameters(id.toString(), transactionStatus)
            )
        }.firstOrNull()?.let {
            serializationService.deserialize<SignedTransactionContainer>(it.array()).toSignedTransaction()
        }
    }

    @Suspendable
    override fun <T: ContractState> findUnconsumedStatesByType(stateClass: Class<out T>): List<StateAndRef<T>> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindUnconsumedStatesByTypeExternalEventFactory::class.java,
                FindUnconsumedStatesByTypeParameters(stateClass)
            )
        }.map { serializationService.deserialize(it.array()) }
    }

    @Suspendable
    override fun persist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        relevantStatesIndexes: List<Int>
    ): List<CordaPackageSummary> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistTransactionExternalEventFactory::class.java,
                PersistTransactionParameters(serialize(transaction.toContainer()), transactionStatus, relevantStatesIndexes)
            )
        }.map { serializationService.deserialize(it.array()) }
    }

    @Suspendable
    override fun updateStatus(id: SecureHash, transactionStatus: TransactionStatus) {
        wrapWithPersistenceException {
            externalEventExecutor.execute(
                UpdateTransactionStatusExternalEventFactory::class.java,
                UpdateTransactionStatusParameters(id.toString(), transactionStatus)
            )
        }
    }

    @Suspendable
    override fun persistIfDoesNotExist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus
    ): Pair<TransactionExistenceStatus, List<CordaPackageSummary>> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistTransactionIfDoesNotExistExternalEventFactory::class.java,
                PersistTransactionIfDoesNotExistParameters(serialize(transaction.toContainer()), transactionStatus)
            )
        }.first().let {
            val (status, summaries) = serializationService.deserialize<Pair<String?, List<CordaPackageSummary>>>(it.array())
            when (status) {
                null -> TransactionExistenceStatus.DOES_NOT_EXIST
                "U" -> TransactionExistenceStatus.UNVERIFIED
                "V" -> TransactionExistenceStatus.VERIFIED
                else -> throw IllegalStateException("Invalid status $status")
            } to summaries
        }
    }

    private fun SignedTransactionContainer.toSignedTransaction(): UtxoSignedTransaction {
        return utxoSignedTransactionFactory.create(wireTransaction, signatures)
    }

    private fun UtxoSignedTransaction.toContainer() =
        (this as UtxoSignedTransactionInternal).run {
            SignedTransactionContainer(wireTransaction, signatures)
        }

    private fun serialize(payload: Any) = ByteBuffer.wrap(serializationService.serialize(payload).bytes)
}
