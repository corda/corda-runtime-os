package net.corda.v5.ledger.transactions

import net.corda.v5.application.node.NetworkParameters
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.contracts.TransactionState

/** A transaction with fully resolved components, such as input states. */
@DoNotImplement
interface FullTransaction : BaseTransaction {
    override val inputs: List<StateAndRef<ContractState>>
    override val references: List<StateAndRef<ContractState>>
    override val outputs: List<TransactionState<ContractState>>

    /**
     * Network parameters that were in force when this transaction was created. Resolved from the hash of network parameters on the corresponding
     * wire transaction.
     */
    val membershipParameters: NetworkParameters?
}
