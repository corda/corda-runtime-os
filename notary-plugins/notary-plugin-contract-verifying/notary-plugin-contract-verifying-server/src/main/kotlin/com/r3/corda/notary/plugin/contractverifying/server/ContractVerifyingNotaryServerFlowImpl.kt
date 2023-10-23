package com.r3.corda.notary.plugin.contractverifying.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@InitiatedBy(protocol = "com.r3.corda.notary.plugin.contractverifying", version = [1])
class ContractVerifyingNotaryServerFlowImpl() : ResponderFlow {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
    }

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    // TODO Dummy logic for now
    @Suspendable
    override fun call(session: FlowSession) {
        try {
            session.receive(ContractVerifyingNotarizationPayload::class.java)

            session.send(
                NotarizationResponse(
                    emptyList(),
                    null
                )
            )
        } catch (e: Exception) {
            logger.warn("Error while processing request from client. Cause: $e ${e.stackTraceToString()}")
            session.send(
                NotarizationResponse(
                    emptyList(),
                    NotaryExceptionGeneral("Error while processing request from client. " +
                            "Please contact notary operator for further details.")
                )
            )
        }
    }
}
