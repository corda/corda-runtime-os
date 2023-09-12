package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackChainResolutionVersion
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.utilities.trace
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The V2 protocol is an extension of the V1 protocol, which can be enabled via a switch (on both sides).
 * In order to avoid huge code duplication, we kept V1 class implementing both protocols and added a switch that makes
 * it behave according to the V2 protocol.
 */

@CordaSystemFlow
class TransactionBackchainSenderFlowV1(
    private val headTransactionIds: Set<SecureHash>,
    private val session: FlowSession,
    val version: TransactionBackChainResolutionVersion
) : SubFlow<Unit> {

    constructor (
        headTransactionId: SecureHash,
        session: FlowSession,
        version: TransactionBackChainResolutionVersion
    ) : this(setOf(headTransactionId), session, version)

    private companion object {
        val log: Logger = LoggerFactory.getLogger(TransactionBackchainSenderFlowV1::class.java)
    }

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService

    @Suspendable
    override fun call() {
        log.trace {
            "Backchain resolution of $headTransactionIds - Waiting to be told what transactions to send to ${session.counterparty} " +
                    "so that the backchain can be resolved"
        }
        while (true) {
            when (val request = session.receive(TransactionBackchainRequestV1::class.java)) {
                is TransactionBackchainRequestV1.Get -> {
                    val transactions = request.transactionIds.map { id ->
                        utxoLedgerPersistenceService.findSignedTransaction(id)
                            ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                    }
                    session.send(transactions)
                    log.trace {
                        "Backchain resolution of $headTransactionIds - Sent backchain transactions ${transactions.map { it.id }} to " +
                                session.counterparty
                    }
                }

                is TransactionBackchainRequestV1.Stop -> {
                    log.trace {
                        "Backchain resolution of $headTransactionIds - Received stop, finishing sending of backchain transaction to " +
                                session.counterparty
                    }
                    return
                }
                is TransactionBackchainRequestV1.GetSignedGroupParameters ->
                    handleSignedGroupParametersRequest(request)
            }
        }
    }

    @Suspendable
    private fun handleSignedGroupParametersRequest(request: TransactionBackchainRequestV1.GetSignedGroupParameters) {
        if (version == TransactionBackChainResolutionVersion.V1) {
            // V1 fails earlier with deserialization anyway in the case of SignedGroupParameters requests.
            val message =
                "Backchain resolution of $headTransactionIds - GetSignedGroupParameters is " +
                        "not available in TransactionBackchainSenderFlowV1 V1"
            log.warn(message)
            throw CordaRuntimeException(message)
        }
        val signedGroupParameters =
            utxoLedgerGroupParametersPersistenceService.find(request.groupParametersHash)
                ?: throw CordaRuntimeException(
                    "Requested signed group parameters is not available (${request.groupParametersHash})."
                )
        // Any signed group parameters can be requested regardless if it is related to the resolved transactions
        // or not. But they are not private information. CORE-10543
        session.send(signedGroupParameters)
        log.trace {
            "Backchain resolution of $headTransactionIds - Sent signed group parameters (${request.groupParametersHash}) to " +
                    session.counterparty
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainSenderFlowV1

        if (session != other.session) return false
        if (headTransactionIds != other.headTransactionIds) return false

        return true
    }

    override fun hashCode(): Int {
        return session.hashCode()
    }
}
