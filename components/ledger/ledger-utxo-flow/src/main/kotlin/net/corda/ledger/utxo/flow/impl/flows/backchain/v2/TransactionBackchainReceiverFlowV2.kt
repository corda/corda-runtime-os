package net.corda.ledger.utxo.flow.impl.flows.backchain.v2

import net.corda.crypto.core.parseSecureHash
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.utxo.flow.impl.flows.backchain.TopologicalSort
import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainReceiverFlowV1
import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainRequestV1
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

@CordaSystemFlow
class TransactionBackchainReceiverFlowV2(
    private val initialTransactionIds: Set<SecureHash>,
    private val originalTransactionsToRetrieve: Set<SecureHash>,
    private val session: FlowSession
) : TransactionBackchainReceiverFlowV1(
    initialTransactionIds, originalTransactionsToRetrieve, session
), SubFlow<TopologicalSort> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService

    @CordaInject
    lateinit var signedGroupParametersVerifier: SignedGroupParametersVerifier

    @Suspendable
    override fun retrieveGroupParameters(
        retrievedTransaction: UtxoSignedTransaction
    ) {
        val retrievedTransactionId = retrievedTransaction.id
        val groupParametersHash = parseSecureHash(requireNotNull(
            (retrievedTransaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()
        ) {
            "Received transaction does not have group parameters in its metadata."
        })

        if (utxoLedgerGroupParametersPersistenceService.find(groupParametersHash) != null) {
            return
        }
        log.trace {
            "Backchain resolution of $initialTransactionIds - Retrieving group parameters ($groupParametersHash) " +
                    "for transaction ($retrievedTransactionId)"
        }
        val retrievedSignedGroupParameters = session.sendAndReceive(
            SignedGroupParameters::class.java,
            TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash)
        )
        if (retrievedSignedGroupParameters.hash != groupParametersHash) {
            val message =
                "Backchain resolution of $initialTransactionIds - Requested group parameters: $groupParametersHash, " +
                        "but received: ${retrievedSignedGroupParameters.hash}"
            log.warn(message)
            throw CordaRuntimeException(message)
        }
        signedGroupParametersVerifier.verifySignature(
            retrievedSignedGroupParameters
        )

        utxoLedgerGroupParametersPersistenceService.persistIfDoesNotExist(retrievedSignedGroupParameters)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainReceiverFlowV2

        if (initialTransactionIds != other.initialTransactionIds) return false
        if (originalTransactionsToRetrieve != other.originalTransactionsToRetrieve) return false
        if (session != other.session) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initialTransactionIds.hashCode()
        result = 31 * result + originalTransactionsToRetrieve.hashCode()
        result = 31 * result + session.hashCode()
        return result
    }

}