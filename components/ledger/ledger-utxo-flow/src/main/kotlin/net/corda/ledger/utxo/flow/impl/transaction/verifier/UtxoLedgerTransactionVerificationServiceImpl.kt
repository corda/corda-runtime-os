package net.corda.ledger.utxo.flow.impl.transaction.verifier

import io.micrometer.core.instrument.Timer
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationExternalEventFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationParameters
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceServiceImpl
import net.corda.membership.lib.SignedGroupParameters
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

@Component(
    service = [UtxoLedgerTransactionVerificationService::class, UsedByFlow::class],
    property = [CORDA_SYSTEM_SERVICE],
    scope = PROTOTYPE
)
@Suppress("LongParameterList")
class UtxoLedgerTransactionVerificationServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = SignedGroupParametersVerifier::class)
    private val signedGroupParametersVerifier: SignedGroupParametersVerifier,
) : UtxoLedgerTransactionVerificationService, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun verify(transaction: UtxoLedgerTransaction) {
        recordSuspendable(::transactionVerificationFlowTimer) @Suspendable {

            val signedGroupParameters = transaction.groupParameters as SignedGroupParameters
            signedGroupParametersVerifier.verify(transaction, signedGroupParameters)
            verifyNotaryAllowed(transaction, signedGroupParameters)

            val verificationResult = externalEventExecutor.execute(
                TransactionVerificationExternalEventFactory::class.java,
                TransactionVerificationParameters(
                    serialize(transaction.toContainer()),
                    transaction.getCpkMetadata()
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
    }

    @Suspendable
    fun verify(transactionContainer: SignedLedgerTransactionContainer, utxoLedgerPersistenceService: UtxoLedgerPersistenceService) {
        recordSuspendable(::transactionVerificationFlowTimer) @Suspendable {
            val (container, cpkMetadata, transactionId) =
                extractTransactionData(utxoLedgerPersistenceService, transactionContainer)

            val verificationResult = externalEventExecutor.execute(
                TransactionVerificationExternalEventFactory::class.java,
                TransactionVerificationParameters(
                    serialize(container),
                    cpkMetadata
                )
            )

            if (verificationResult.status != TransactionVerificationStatus.VERIFIED) {
                throw TransactionVerificationException(
                    transactionId,
                    verificationResult.status,
                    verificationResult.errorType,
                    verificationResult.errorMessage
                )
            }
        }
    }

    /**
     * Move to a function so there's no UtxoSignedLedgerTransaction instances on the stack
     */
    private fun extractTransactionData(
        utxoLedgerPersistenceService: UtxoLedgerPersistenceService,
        transactionContainer: SignedLedgerTransactionContainer
    ): Triple<UtxoLedgerTransactionContainer, List<CordaPackageSummary>, SecureHash> {
        var transaction =
            (utxoLedgerPersistenceService as UtxoLedgerPersistenceServiceImpl).turnContainerIntoTransaction(
                transactionContainer
            )
        // extract and store everything we need from the transaction before a suspend function is called
        val container = transaction.toContainer()
        val cpkMetadata = transaction.getCpkMetadata()
        val transactionId = transaction.id

        val signedGroupParameters = transaction.groupParameters as SignedGroupParameters
        signedGroupParametersVerifier.verify(transaction, signedGroupParameters)
        verifyNotaryAllowed(transaction, signedGroupParameters)

        // not safe to use transaction after a call to suspend
        return Triple(container, cpkMetadata, transactionId)
    }

    private fun UtxoLedgerTransaction.toContainer() =
        (this as UtxoLedgerTransactionInternal).run {
            UtxoLedgerTransactionContainer(
                wireTransaction,
                inputStateAndRefs,
                referenceStateAndRefs,
                this.groupParameters as SignedGroupParameters
            )
        }

    private fun UtxoLedgerTransaction.getCpkMetadata() =
        (this as UtxoLedgerTransactionInternal).run {
            (wireTransaction.metadata as TransactionMetadataInternal).getCpkMetadata()
        }

    private fun serialize(payload: Any) = ByteBuffer.wrap(serializationService.serialize(payload).bytes)

    private fun transactionVerificationFlowTimer(): Timer {
        return CordaMetrics.Metric.Ledger.TransactionVerificationFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .build()
    }
}
