package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus.INVALID
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceServiceImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationServiceImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.utilities.trace
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory

@Component(
    service = [TransactionBackchainVerifier::class, UsedByFlow::class],
    property = [CORDA_SYSTEM_SERVICE],
    scope = PROTOTYPE
)
class TransactionBackchainVerifierImpl @Activate constructor(
    @Reference(service = UtxoLedgerPersistenceService::class)
    private val utxoLedgerPersistenceService: UtxoLedgerPersistenceService,
    @Reference(service = UtxoLedgerTransactionVerificationService::class)
    private val utxoLedgerTransactionVerificationService: UtxoLedgerTransactionVerificationService,
    @Reference(service = VisibilityChecker::class)
    private val visibilityChecker: VisibilityChecker,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : TransactionBackchainVerifier, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun verify(initialTransactionIds: Set<SecureHash>, topologicalSort: TopologicalSort): Boolean {
        val sortedTransactions = topologicalSort.complete().iterator()

        // Hack: cast here so I don't have to change the api repo
        val interalPersistenceService = utxoLedgerPersistenceService as UtxoLedgerPersistenceServiceImpl

        for (transactionId in sortedTransactions) {
            val (transactionContainer, status) = interalPersistenceService.findSignedLedgerTransactionContainerWithStatus(
                transactionId,
                UNVERIFIED
            ) ?: throw CordaRuntimeException("Transaction does not exist locally")
            when (status) {
                INVALID -> {
                    log.warn(
                        "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed. " +
                                "The transaction is already invalid."
                    )
                    return false
                }
                VERIFIED -> {
                    log.trace {
                        "Backchain resolution of $initialTransactionIds - transaction $transactionId is already verified, " +
                                "skipping verification."
                    }
                }
                UNVERIFIED -> {
                    if (transactionContainer == null) {
                        log.warn(
                            "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed. " +
                                    "The transaction disappeared."
                        )
                        return false
                    }
                    try {
                        log.trace { "Backchain resolution of $initialTransactionIds - Verifying transaction $transactionId" }

                        // This functionality needs to deal in UtxoSignedLedgerTransaction types, move it into a local function to
                        // ensure they never end up on the stack at the point of calling a suspendable
                        verifyTransactionSignatures(interalPersistenceService, transactionContainer)

                        // Hack so I didn't have to change the api
                        val internalService = utxoLedgerTransactionVerificationService as UtxoLedgerTransactionVerificationServiceImpl

                        // replace the call to verify with our new one which takes only a "safe" transaction container
                        //utxoLedgerTransactionVerificationService.verify(transaction)

                        internalService.verify(transactionContainer, utxoLedgerPersistenceService)

                        log.trace { "Backchain resolution of $initialTransactionIds - Verified transaction $transactionId" }
                    } catch (e: Exception) {
                        // TODO revisit what exceptions get caught
                        log.warn(
                            "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed," +
                                    " message: ${e.message}"
                        )
                        return false
                    }

                    // This functionality needs to deal in UtxoSignedLedgerTransaction types, move it into a local function to
                    // ensure they never end up on the stack at the point of calling a suspendable
                    val (signedTransactionContainer, visibleStatesIndexes) = getTransactionContainerAndVisibileStatebIndexes(
                        interalPersistenceService,
                        transactionContainer
                    )

                    // replace the call to persistFromContainer with our new one which takes only a "safe" transaction container
                    interalPersistenceService.persistFromContainer(signedTransactionContainer, VERIFIED, visibleStatesIndexes)
                    log.trace { "Backchain resolution of $initialTransactionIds - Stored transaction $transactionId as verified" }
                }

                else -> {
                    log.warn(
                        "Backchain resolution of $initialTransactionIds - Verification of transaction $transactionId failed. " +
                                "Unexpected status $status"
                    )
                    return false
                }
            }
            sortedTransactions.remove()
        }

        return true
    }

    /**
     * There's some complexity here as contract.isVisible is a suspendable, so we have to isolate that and ensure no
     * UtxoSignedLedgerTransaction ends up on the stack at the point it's called.
     */
    @Suspendable
    private fun getTransactionContainerAndVisibileStatebIndexes(
        interalPersistenceService: UtxoLedgerPersistenceServiceImpl,
        transactionContainer: SignedLedgerTransactionContainer
    ): Pair<SignedTransactionContainer, List<Int>> {
        val (signedTransactionContainer, stateAndRefs) = containerAndStateAndRefs(interalPersistenceService, transactionContainer)
        val visibleStatesIndexes = localGetVisibleStateIndexes(stateAndRefs, visibilityChecker)
        return Pair(signedTransactionContainer, visibleStatesIndexes)
    }

    private fun containerAndStateAndRefs(
        interalPersistenceService: UtxoLedgerPersistenceServiceImpl,
        transactionContainer: SignedLedgerTransactionContainer
    ): Pair<SignedTransactionContainer, List<StateAndRef<*>>> {
        val transaction = interalPersistenceService.turnContainerIntoTransaction(transactionContainer)
        val signedTransactionContainer = transaction.toContainer()
        val stateAndRefs = transaction.getStateAndRefs()
        return Pair(signedTransactionContainer, stateAndRefs)
    }

    @Suspendable
    fun localGetVisibleStateIndexes(stateAndRefs: List<StateAndRef<*>>, checker: VisibilityChecker): List<Int> {
        val result = mutableListOf<Int>()

        stateAndRefs.forEachIndexed { index, stateAndRef ->
            val contract = stateAndRef.state.contractType.getConstructor().newInstance()
            if (contract.isVisible(stateAndRef.state.contractState, checker)) {
                result.add(index)
            }
        }

        return result
    }

    fun UtxoSignedTransactionInternal.getStateAndRefs(): List<StateAndRef<*>> {
        val result = mutableListOf<StateAndRef<*>>()

        for (index in outputStateAndRefs.indices) {
            val stateAndRef = outputStateAndRefs[index]
            result.add(stateAndRef)
        }

        return result
    }

    /**
     * Move to a function so there's no UtxoSignedLedgerTransaction instances on the stack
     */
    private fun verifyTransactionSignatures(
        interalPersistenceService: UtxoLedgerPersistenceServiceImpl,
        transactionContainer: SignedLedgerTransactionContainer
    ) {
        // Safely turn the container into a transaction temporarily - no suspend function calls here
        val transaction = interalPersistenceService.turnContainerIntoTransaction(transactionContainer)
        transaction.verifySignatorySignatures()
        transaction.verifyAttachedNotarySignature()
        // transaction shouldn't be used again
    }

    private fun UtxoSignedTransaction.toContainer(): SignedTransactionContainer {
        return (this as UtxoSignedTransactionInternal).run {
            SignedTransactionContainer(wireTransaction, signatures)
        }
    }
}
