package net.corda.ledger.utxo.flow.impl.flows.backchain.v2

import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainRequestV1
import net.corda.ledger.utxo.flow.impl.flows.backchain.v1.TransactionBackchainSenderFlowV1
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class TransactionBackchainSenderFlowV2(
    private val headTransactionIds: Set<SecureHash>,
    private val session: FlowSession
) : TransactionBackchainSenderFlowV1(headTransactionIds, session), SubFlow<Unit> {

    constructor (headTransactionId: SecureHash, session: FlowSession) : this(setOf(headTransactionId), session)

    private companion object {
        val log: Logger = LoggerFactory.getLogger(TransactionBackchainSenderFlowV2::class.java)
    }

    @CordaInject
    lateinit var utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService

    override fun handleSignedGroupParametersRequest(request: TransactionBackchainRequestV1.GetSignedGroupParameters) {
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

        other as TransactionBackchainSenderFlowV2

        if (session != other.session) return false
        if (headTransactionIds != other.headTransactionIds) return false

        return true
    }

    override fun hashCode(): Int {
        return session.hashCode()
    }
}
