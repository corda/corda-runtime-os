package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationExternalEventFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationParameters
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.nio.ByteBuffer

@Component(
    service = [ UtxoLedgerTransactionVerificationService::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
class UtxoLedgerTransactionVerificationServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : UtxoLedgerTransactionVerificationService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun verify(transaction: UtxoLedgerTransaction) {
        val verificationResult = externalEventExecutor.execute(
            TransactionVerificationExternalEventFactory::class.java,
            TransactionVerificationParameters(
                serialize(transaction.toContainer()),
                transaction.getCpkMetadata().map {
                    CordaPackageSummary(it.name, it.version, it.signerSummaryHash, it.fileChecksum)
                }
            )
        )

        if (verificationResult.status != TransactionVerificationStatus.VERIFIED) {
            throw TransactionVerificationException(
                transaction.id,
                verificationResult.status,
                verificationResult.errorType,
                verificationResult.errorMessage
            )
        }
    }

    private fun UtxoLedgerTransaction.toContainer() =
        (this as UtxoLedgerTransactionInternal).run {
            UtxoLedgerTransactionContainer(wireTransaction, inputStateAndRefs, referenceStateAndRefs)
        }

    private fun UtxoLedgerTransaction.getCpkMetadata() =
        (this as UtxoLedgerTransactionInternal).run {
            (wireTransaction.metadata as TransactionMetadataInternal).getCpkMetadata()
        }

    private fun serialize(payload: Any) = ByteBuffer.wrap(serializationService.serialize(payload).bytes)
}
