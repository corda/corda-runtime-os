package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionParameters
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionParameters
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.nio.ByteBuffer

@Component(
    service = [ UtxoLedgerPersistenceService::class, UsedByFlow::class ],
    property = [ "corda.system=true" ],
    scope = PROTOTYPE
)
class UtxoLedgerPersistenceServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService
) : UtxoLedgerPersistenceService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun find(id: SecureHash): UtxoSignedTransaction? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindTransactionExternalEventFactory::class.java,
                FindTransactionParameters(id.toString())
            )
        }.firstOrNull()?.let {
            serializationService.deserialize<SignedTransactionContainer>(it.array()).toSignedTransaction()
        }
    }

    @Suspendable
    override fun persist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus
    ): List<CordaPackageSummaryImpl> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistTransactionExternalEventFactory::class.java,
                PersistTransactionParameters(serialize(transaction.toContainer()), transactionStatus.value)
            )
        }.map { serializationService.deserialize(it.array()) }
    }

    private fun SignedTransactionContainer.toSignedTransaction() =
        UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signatures
        )

    private fun UtxoSignedTransaction.toContainer() =
        (this as UtxoSignedTransactionInternal).run {
            SignedTransactionContainer(wireTransaction, signatures)
        }

    private fun serialize(payload: Any) = ByteBuffer.wrap(serializationService.serialize(payload).bytes)
}
