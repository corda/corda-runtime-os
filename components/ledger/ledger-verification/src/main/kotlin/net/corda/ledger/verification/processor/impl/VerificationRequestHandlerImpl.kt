package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.data.transaction.ContractVerificationStatus
import net.corda.ledger.utxo.contract.verification.ContractVerificationFailure as ContractVerificationFailureAvro
import net.corda.ledger.utxo.contract.verification.VerificationResult as VerificationResultAvro
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest as VerifyContractsRequestAvro
import net.corda.ledger.utxo.contract.verification.VerifyContractsResponse as VerifyContractsResponseAvro
import net.corda.ledger.utxo.data.transaction.ContractVerificationResult
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.verification.processor.VerificationRequestHandler
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.ledger.utxo.ContractVerificationFailure

class VerificationRequestHandlerImpl: VerificationRequestHandler {

    override fun handleRequest(sandbox: SandboxGroupContext, request: VerifyContractsRequestAvro): VerifyContractsResponseAvro {
        val serializationService = sandbox.getSandboxSingletonService<SerializationService>()
        val ledgerTransaction = request.getLedgerTransaction(serializationService)
        val verificationResult = verifyTransactionContracts(ledgerTransaction)
        return verificationResult.toAvro()
    }

    private fun ContractVerificationResult.toAvro() =
        VerifyContractsResponseAvro(
            status.toAvro(),
            failureReasons.map{ it.toAvro() }
        )

    private fun ContractVerificationStatus.toAvro() = when(this) {
        ContractVerificationStatus.INVALID -> VerificationResultAvro.INVALID
        ContractVerificationStatus.VERIFIED -> VerificationResultAvro.VERIFIED
    }

    private fun ContractVerificationFailure.toAvro() =
        ContractVerificationFailureAvro(
            contractClassName,
            contractStateClassNames,
            exceptionClassName,
            exceptionMessage
        )

    private fun VerifyContractsRequestAvro.getLedgerTransaction(serializationService: SerializationService) =
        serializationService.deserialize<UtxoLedgerTransactionContainer>(transaction.array()).run {
            UtxoLedgerTransactionImpl(
                WrappedUtxoWireTransaction(wireTransaction, serializationService),
                inputStateAndRefs,
                referenceStateAndRefs
            )
        }
}