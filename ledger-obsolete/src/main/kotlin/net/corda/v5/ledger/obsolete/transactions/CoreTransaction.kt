package net.corda.v5.ledger.obsolete.transactions

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.obsolete.contracts.ContractState
import net.corda.v5.ledger.obsolete.contracts.ContractStateData
import net.corda.v5.ledger.obsolete.contracts.StateRef

/**
 * A transaction with the minimal amount of information required to compute the unique transaction [id], and
 * resolve a [FullTransaction]. This type of transaction, wrapped in [SignedTransaction], gets transferred across the
 * wire and recorded to storage.
 */
@DoNotImplement
interface CoreTransaction : BaseTransaction {
    /** The inputs of this transaction, containing state references only. **/
    override val inputs: List<StateRef>
    /** The reference inputs of this transaction, containing the state references only. **/
    override val references: List<StateRef>
    /** Outputs of this transaction represent as ContractStateData */
    override val outputs: List<ContractStateData<ContractState>>
    /**
     * Hash of the group parameters that were in force when the transaction was notarised. Null means, that the transaction
     * was created on older version of Corda (before 4), resolution will default to initial parameters.
     */
    val membershipParametersHash: SecureHash?
}

