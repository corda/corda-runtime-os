package net.corda.systemflows

import java.security.PublicKey
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowException
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.flows.receive
import net.corda.v5.application.flows.unwrap
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.crypto.KeyManagementService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.ledger.services.TransactionMappingService
import net.corda.v5.ledger.services.TransactionService
import net.corda.v5.ledger.transactions.SignedTransaction

abstract class SignTransactionFlow constructor(val otherSideSession: FlowSession) : Flow<SignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine
    @CordaInject
    lateinit var keyManagementService: KeyManagementService
    @CordaInject
    lateinit var transactionService: TransactionService
    @CordaInject
    lateinit var transactionMappingService: TransactionMappingService

    @Suspendable
    override fun call(): SignedTransaction {
        log.debug { "Receiving transaction to sign from session: $otherSideSession" }
        // Receive transaction and resolve dependencies, check sufficient signatures is disabled as we don't have all signatures.
        val stx = flowEngine.subFlow(ReceiveTransactionFlow(otherSideSession, checkSufficientSignatures = false))
        // Receive the signing key that the party requesting the signature expects us to sign with. Having this provided
        // means we only have to check we own that one key, rather than matching all keys in the transaction against all
        // keys we own.
        val signingKeys = otherSideSession.receive<List<PublicKey>>().unwrap { keys ->
            keyManagementService.filterMyKeys(keys)
        }
        log.debug { "Verifying transaction: ${stx.id}" }
        // Check that the Responder actually needs to sign.
        checkMySignaturesRequired(stx, signingKeys)
        // Check the signatures which have already been provided. Usually the Initiators and possibly an Oracle's.
        checkSignatures(stx)
        transactionMappingService.toLedgerTransaction(stx.tx).verify()
        // Perform some custom verification over the transaction.
        try {
            checkTransaction(stx)
        } catch (e: Exception) {
            if (e is IllegalStateException || e is IllegalArgumentException)
                throw FlowException(e)
            else
                throw e
        }
        // Sign and send back our signature to the Initiator.
        log.debug { "Signing transaction: ${stx.id}" }
        val mySignatures = signingKeys.map { key ->
            transactionService.createSignature(stx, key)
        }
        otherSideSession.send(mySignatures)

        // Return the additionally signed transaction.
        return stx + mySignatures
    }

    @Suspendable
    private fun checkSignatures(stx: SignedTransaction) {
        val signed = stx.sigs.mapTo(LinkedHashSet()) { it.by }
        val allSigners = stx.tx.requiredSigningKeys
        val notSigned = allSigners - signed
        transactionService.verifySignaturesExcept(stx, notSigned)
    }

    /**
     * The [checkTransaction] method allows the caller of this flow to provide some additional checks over the proposed
     * transaction received from the counterparty. For example:
     *
     * - Ensuring that the transaction you are receiving is the transaction you *EXPECT* to receive. I.e. is has the
     *   expected type and number of inputs and outputs
     * - Checking that the properties of the outputs are as you would expect. Linking into any reference data sources
     *   might be appropriate here
     * - Checking that the transaction is not incorrectly spending (perhaps maliciously) one of your asset states, as
     *   potentially the transaction creator has access to some of your state references
     *
     * **WARNING**: If appropriate checks, such as the ones listed above, are not defined then it is likely that your
     * node will sign any transaction if it conforms to the contract code in the transaction's referenced contracts.
     *
     * [IllegalArgumentException], [IllegalStateException] and [AssertionError] will be caught and rethrown as flow
     * exceptions i.e. the other side will be given information about what exact check failed.
     *
     * @param stx a partially signed transaction received from your counterparty.
     * @throws FlowException if the proposed transaction fails the checks.
     */
    @Suspendable
    protected abstract fun checkTransaction(stx: SignedTransaction)

    @Suspendable
    private fun checkMySignaturesRequired(stx: SignedTransaction, signingKeys: Iterable<PublicKey>) {
        require(signingKeys.all { it in stx.tx.requiredSigningKeys }) {
            "A signature was requested for a key that isn't part of the required signing keys for transaction ${stx.id}"
        }
    }
}
