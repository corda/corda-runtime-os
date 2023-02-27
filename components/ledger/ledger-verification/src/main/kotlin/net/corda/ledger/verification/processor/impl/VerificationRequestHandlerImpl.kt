package net.corda.ledger.verification.processor.impl

import net.corda.data.ExceptionEnvelope as ExceptionEnvelopeAvro
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.verification.TransactionVerificationStatus as TransactionVerificationStatusAvro
import net.corda.ledger.utxo.verification.TransactionVerificationRequest as TransactionVerificationRequestAvro
import net.corda.ledger.utxo.verification.TransactionVerificationResponse as TransactionVerificationResponseAvro
import net.corda.ledger.utxo.data.transaction.TransactionVerificationResult
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.transaction.verifier.UtxoLedgerTransactionVerifier
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.ledger.verification.sandbox.impl.getSerializationService
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VerificationRequestHandlerImpl(private val responseFactory: ExternalEventResponseFactory): VerificationRequestHandler {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun handleRequest(sandbox: SandboxGroupContext, request: TransactionVerificationRequestAvro): Record<*, *> {
        val serializationService = sandbox.getSerializationService()
        val transactionFactory = { request.getLedgerTransaction(serializationService) }
        val transaction = transactionFactory.invoke()
        return try {
            UtxoLedgerTransactionVerifier(transactionFactory, transaction).verify()
            responseFactory.success(
                request.flowExternalEventContext,
                TransactionVerificationResult(TransactionVerificationStatus.VERIFIED).toAvro()
            )
        } catch (e: Exception) {
            log.error("Error verifying ledger transaction with ID ${transaction.id}", e)
            responseFactory.success(
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
            if (errorType != null) {
                ExceptionEnvelopeAvro(
                    errorType,
                    errorMessage ?: errorType
                )
            } else null
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