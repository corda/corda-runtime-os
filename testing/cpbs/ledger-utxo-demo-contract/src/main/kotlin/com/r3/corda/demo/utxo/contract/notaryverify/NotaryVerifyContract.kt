package com.r3.corda.demo.utxo.contract.notaryverify

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class NotaryVerifyContract : Contract {
    @CordaInject
    lateinit var transactionSignatureVerificationService: TransactionSignatureVerificationService

    override fun verify(transaction: UtxoLedgerTransaction) {
        val outputs = transaction.getOutputStates(NotaryVerifyState::class.java)
        val state = outputs.single()

        val transactionId = state.transactionIdToVerify
        val signature = state.notarySignature
        val notaryPublicKey = state.notaryPublicKey
        transactionSignatureVerificationService.verifySignature(transactionId, signature, notaryPublicKey)
    }

    override fun isVisible(state: ContractState, checker: VisibilityChecker): Boolean = true
}