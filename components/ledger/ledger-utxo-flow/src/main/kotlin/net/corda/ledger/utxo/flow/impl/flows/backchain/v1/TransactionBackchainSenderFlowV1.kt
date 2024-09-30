package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.ledger.lib.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * V1 changed slightly between 5.0 and 5.1. (5.1 supports distributing SignedGroupParameters.)
 * This change is not managed through flow versioning since flow interoperability is not supported between these versions.
 */

@CordaSystemFlow
class TransactionBackchainSenderFlowV1(
    private val headTransactionIds: Set<SecureHash>,
    private val session: FlowSession
) : SubFlow<Unit> {

    constructor (
        headTransactionId: SecureHash,
        session: FlowSession
    ) : this(setOf(headTransactionId), session)

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
                            ?: run {
                                log.warn(
                                    "Transaction $id does not exist locally when requested during backchain resolution. A filtered " +
                                        "transaction might exist for the same id or the transaction has been deleted locally. " +
                                        "Sending a backchain containing a filtered transaction suggests incorrect mixing of states " +
                                        "and transactions in enhanced privacy and non-enhanced privacy mode."
                                )
                                throw CordaRuntimeException(
                                    "Transaction $id does not exist locally when requested during backchain resolution. A filtered " +
                                        "transaction might exist for the same id or the transaction has been deleted locally."
                                )
                            }
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
