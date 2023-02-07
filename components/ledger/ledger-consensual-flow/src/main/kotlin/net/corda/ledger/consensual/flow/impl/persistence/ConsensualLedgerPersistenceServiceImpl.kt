package net.corda.ledger.consensual.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.FindTransactionParameters
import net.corda.ledger.consensual.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.consensual.flow.impl.persistence.external.events.PersistTransactionParameters
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.nio.ByteBuffer

@Component(
    service = [ ConsensualLedgerPersistenceService::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
class ConsensualLedgerPersistenceServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService
) : ConsensualLedgerPersistenceService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun find(id: SecureHash): ConsensualSignedTransaction? {
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
        transaction: ConsensualSignedTransaction,
        transactionStatus: TransactionStatus
    ): List<CordaPackageSummary> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistTransactionExternalEventFactory::class.java,
                PersistTransactionParameters(serialize(transaction.toContainer()), transactionStatus.value)
            )
        }.map { serializationService.deserialize(it.array()) }
    }

    private fun SignedTransactionContainer.toSignedTransaction() =
        ConsensualSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signatures
        )

    private fun ConsensualSignedTransaction.toContainer() =
        (this as ConsensualSignedTransactionInternal).run {
            SignedTransactionContainer(wireTransaction, signatures)
        }

    private fun serialize(payload: Any) = ByteBuffer.wrap(serializationService.serialize(payload).bytes)
}
