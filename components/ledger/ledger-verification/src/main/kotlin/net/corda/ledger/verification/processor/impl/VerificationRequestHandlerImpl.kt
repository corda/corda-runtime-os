package net.corda.ledger.verification.processor.impl

import net.corda.data.ExceptionEnvelope as ExceptionEnvelopeAvro
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.verification.TransactionVerificationStatus as TransactionVerificationStatusAvro
import net.corda.ledger.utxo.verification.TransactionVerificationRequest as TransactionVerificationRequestAvro
import net.corda.ledger.utxo.verification.TransactionVerificationResponse as TransactionVerificationResponseAvro
import net.corda.ledger.utxo.data.transaction.TransactionVerificationResult
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.transaction.verifier.UtxoLedgerTransactionVerifier
import net.corda.ledger.verification.processor.ResponseFactory
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize

class VerificationRequestHandlerImpl(private val responseFactory: ResponseFactory): VerificationRequestHandler {

    override fun handleRequest(sandbox: SandboxGroupContext, request: TransactionVerificationRequestAvro): Record<*, *> {
        val serializationService = sandbox.getSandboxSingletonService<SerializationService>()
        val ledgerTransaction = request.getLedgerTransaction(serializationService)
        return try {
            UtxoLedgerTransactionVerifier(ledgerTransaction).verify()
            responseFactory.successResponse(
                request.flowExternalEventContext,
                TransactionVerificationResult(TransactionVerificationStatus.VERIFIED).toAvro()
            )
        } catch (e: Exception) {
            responseFactory.successResponse(
                request.flowExternalEventContext,
                TransactionVerificationResult(
                    TransactionVerificationStatus.INVALID,
                    e::class.java.canonicalName,
                    e.message
                ).toAvro()
            )
        }
    }

    private fun TransactionVerificationResult.toAvro() =
        TransactionVerificationResponseAvro(
            status.toAvro(),
            ExceptionEnvelopeAvro(
                errorType,
                errorMessage
            )
        )

    private fun TransactionVerificationStatus.toAvro() = when(this) {
        TransactionVerificationStatus.INVALID -> TransactionVerificationStatusAvro.INVALID
        TransactionVerificationStatus.VERIFIED -> TransactionVerificationStatusAvro.VERIFIED
    }

    private fun TransactionVerificationRequestAvro.getLedgerTransaction(serializationService: SerializationService) =
        serializationService.deserialize<UtxoLedgerTransactionContainer>(transaction.array()).run {
            UtxoLedgerTransactionImpl(
                WrappedUtxoWireTransaction(wireTransaction, serializationService),
                inputStateAndRefs,
                referenceStateAndRefs
            )
        }
}