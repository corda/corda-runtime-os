package net.corda.packaging.test.contract

import net.corda.v5.ledger.contracts.Contract
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.identity.AbstractParty
import net.corda.v5.ledger.transactions.LedgerTransaction

class PackagingTestState : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf()
}

class PackagingTestContract : Contract {
    override fun verify(tx: LedgerTransaction) {}
}