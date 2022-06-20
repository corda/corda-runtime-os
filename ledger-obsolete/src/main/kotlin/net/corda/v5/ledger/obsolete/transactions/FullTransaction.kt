package net.corda.v5.ledger.obsolete.transactions

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.obsolete.contracts.ContractState
import net.corda.v5.ledger.obsolete.contracts.StateAndRef
import net.corda.v5.ledger.obsolete.contracts.TransactionState
import net.corda.v5.membership.GroupParameters

/** A transaction with fully resolved components, such as input states. */
@DoNotImplement
interface FullTransaction : BaseTransaction {
    override val inputs: List<StateAndRef<ContractState>>
    override val references: List<StateAndRef<ContractState>>
    override val outputs: List<TransactionState<ContractState>>

    /**
     * Group parameters that were in force when this transaction was created. Resolved from the hash of group parameters on the corresponding
     * wire transaction.
     */
    val membershipParameters: GroupParameters?
}
