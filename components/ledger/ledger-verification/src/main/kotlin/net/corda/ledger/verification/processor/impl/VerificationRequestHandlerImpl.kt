package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.verification.processor.ResponseFactory
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.utilities.time.UTCClock

class VerificationRequestHandlerImpl(private val responseFactory: ResponseFactory): VerificationRequestHandler {

    override fun handleRequest(sandbox: SandboxGroupContext, request: VerifyContractsRequest): Record<*, *> {

        val verificationService = VerificationServiceImpl(
            sandbox.getSandboxSingletonService(),
            sandbox.getSandboxSingletonService(),
            UTCClock()
        )

        val transaction = UtxoTransactionReaderImpl(
            sandbox,
            request.flowExternalEventContext,
            request.transaction.array()
        )

        val verificationResult = verificationService.verifyContracts(transaction)

        return responseFactory.successResponse(
            request.flowExternalEventContext,
            verificationResult
        )
    }
}