package com.r3.corda.demo.utxo.contract

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class TestContract : Contract {
//    @CordaInject
//    lateinit var serializationService: SerializationService
//
//    @CordaInject
//    lateinit var digestService: DigestService
//
//    @CordaInject
//    lateinit var digitalSignatureVerificationService: DigitalSignatureVerificationService

    override fun verify(transaction: UtxoLedgerTransaction) {
//        serializationService.serialize('s')
    }

    override fun isVisible(state: ContractState, checker: VisibilityChecker): Boolean {
        return when (state) {
            is TestUtxoState -> state.identifier % 2 == 0
            else -> false
        }
    }
}