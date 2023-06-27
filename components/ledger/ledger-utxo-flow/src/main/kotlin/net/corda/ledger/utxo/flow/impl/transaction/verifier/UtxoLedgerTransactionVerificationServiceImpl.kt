package net.corda.ledger.utxo.flow.impl.transaction.verifier

import io.micrometer.core.instrument.Timer
import net.corda.crypto.core.parseSecureHash
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.metrics.recordSuspendable
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.groupparameters.CurrentGroupParametersService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationExternalEventFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.external.events.TransactionVerificationParameters
import net.corda.ledger.utxo.transaction.verifier.SignedGroupParametersVerifier
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
    @Reference(service = UtxoLedgerGroupParametersPersistenceService::class)
    private val utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService,
    @Reference(service = CurrentGroupParametersService::class)
    private val currentGroupParametersService: CurrentGroupParametersService,
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = SignedGroupParametersVerifier::class)
    private val signedGroupParametersVerifier: SignedGroupParametersVerifier,
) : UtxoLedgerTransactionVerificationService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun verify(transaction: UtxoLedgerTransaction) {
        recordSuspendable(::transactionVerificationFlowTimer) @Suspendable {

            val signedGroupParameters = fetchAndVerifySignedGroupParameters(transaction)
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
    override fun initialVerify(transaction: UtxoLedgerTransaction) {
        verifyCurrenGroupParametersUsed(transaction)
        verify(transaction)
    }

    private fun verifyCurrenGroupParametersUsed(transaction: UtxoLedgerTransaction){
        check(
            transaction.getMembershipGroupParametersHash() ==
                    currentGroupParametersService.get().hash.toString()
        ) {
            "Transactions can be created only with the latest membership group parameters."
        }
    }

    @Suspendable
    private fun fetchAndVerifySignedGroupParameters(transaction: UtxoLedgerTransaction): SignedGroupParameters {
        val membershipGroupParametersHashString = transaction.getMembershipGroupParametersHash()

        val currentGroupParameters = currentGroupParametersService.get()
        val signedGroupParameters =
            if (currentGroupParameters.hash.toString() == membershipGroupParametersHashString) {
                currentGroupParameters
            } else {
                val membershipGroupParametersHash = parseSecureHash(membershipGroupParametersHashString)
                utxoLedgerGroupParametersPersistenceService.find(membershipGroupParametersHash)
            }
        requireNotNull(signedGroupParameters) {
            "Signed group parameters $membershipGroupParametersHashString related to the transaction ${transaction.id} cannot be accessed."
        }
        signedGroupParametersVerifier.verify(
            transaction,
            signedGroupParameters,
            currentGroupParametersService.getMgmKeys()
        )
        return signedGroupParameters
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

    private fun UtxoLedgerTransaction.getMembershipGroupParametersHash(): String {
        return requireNotNull((metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()) {
            "Membership group parameters hash cannot be found in the transaction metadata."
        }
    }

    private fun transactionVerificationFlowTimer(): Timer {
        return CordaMetrics.Metric.Ledger.TransactionVerificationFlowTime
            .builder()
            .forVirtualNode(currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity.shortHash.toString())
            .build()
    }
}
