package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.verification.processor.ResponseFactory
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize

class VerificationRequestHandlerImpl(private val responseFactory: ResponseFactory): VerificationRequestHandler {

    override fun handleRequest(sandbox: SandboxGroupContext, request: VerifyContractsRequest): Record<*, *> {
        val serializationService = sandbox.getSandboxSingletonService<SerializationService>()
        val ledgerTransaction = request.getLedgerTransaction(serializationService)
        val verifier = UtxoLedgerTransactionContractVerifier(ledgerTransaction)
        val verificationResult = verifier.verify()
        return responseFactory.successResponse(
            request.flowExternalEventContext,
            verificationResult
        )
    }

    private fun VerifyContractsRequest.getLedgerTransaction(serializationService: SerializationService) =
        serializationService.deserialize<UtxoLedgerTransactionContainer>(transaction.array()).run {
            UtxoLedgerTransactionImpl(
                WrappedUtxoWireTransaction(wireTransaction, serializationService),
                inputStateAndRefs,
                referenceStateAndRefs
            )
        }
}