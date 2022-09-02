package net.cordapp.libs.packaging.test.contract

import net.corda.v5.ledger.obsolete.contracts.Contract
import net.corda.v5.ledger.obsolete.contracts.ContractState
import net.corda.v5.ledger.obsolete.identity.AbstractParty
import net.corda.v5.ledger.obsolete.transactions.LedgerTransaction

class PackagingTestState : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf()
}

class PackagingTestContract : Contract {
    override fun verify(tx: LedgerTransaction) {}
}