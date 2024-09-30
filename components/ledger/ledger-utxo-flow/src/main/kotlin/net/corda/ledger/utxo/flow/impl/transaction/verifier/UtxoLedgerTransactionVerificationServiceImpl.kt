package net.corda.ledger.utxo.flow.impl.transaction.verifier

import io.micrometer.core.instrument.Timer
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.lib.utxo.flow.impl.groupparameters.SignedGroupParametersVerifier
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.membership.lib.SignedGroupParameters
import net.corda.metrics.CordaMetrics
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

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

    @Suspendable
    override fun verify(transaction: UtxoLedgerTransaction) {
        recordSuspendable(::transactionVerificationFlowTimer) @Suspendable {
            val signedGroupParameters = transaction.groupParameters as SignedGroupParameters
            signedGroupParametersVerifier.verify(transaction, signedGroupParameters)
            verifyNotaryAllowed(transaction, signedGroupParameters)

            // Now context switch to the verification worker, see [VerificationRequestHandlerImpl.handleRequest],
            // passing in the whole ledger transaction (serialised as AMQP), and the contracts will be checked there.
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

    private fun serialize(payload: Any) = serializationService.serialize(payload).bytes

    private fun transactionVerificationFlowTimer(): Timer {
        return CordaMetrics.Metric.Ledger.TransactionVerificationFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .build()
    }
}
