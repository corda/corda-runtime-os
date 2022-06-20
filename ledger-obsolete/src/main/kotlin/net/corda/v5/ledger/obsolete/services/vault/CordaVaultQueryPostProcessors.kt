package net.corda.v5.ledger.obsolete.services.vault

import net.corda.v5.ledger.obsolete.contracts.ContractState
import net.corda.v5.ledger.obsolete.contracts.StateAndRef
import java.util.stream.Stream

/**
 * Identity function post-processor that returns [ContractState]s with no side-effects.
 *
 * Corda attempts to load [StateAndRef]s from the named query results and applies this pass-through post-processor to return `ContractStates`.
 */
class IdentityContractStatePostProcessor : StateAndRefPostProcessor<ContractState> {
    companion object {
        const val POST_PROCESSOR_NAME = "Corda.IdentityContractStatePostProcessor"
    }
    override fun postProcess(inputs: Stream<StateAndRef<ContractState>>): Stream<ContractState> {
        return inputs.map { it.state.data }
    }
    override val name = POST_PROCESSOR_NAME
    override val availableForRPC = true
}

/**
 * Simple post-processor that just returns [StateAndRef]s with no side-effects.
 *
 * Corda attempts to load [StateAndRef]s from the named query results and applies this pass-through post-processor to return the `StateAndRef`s.
 */
class IdentityStateAndRefPostProcessor : StateAndRefPostProcessor<StateAndRef<ContractState>> {
    companion object {
        const val POST_PROCESSOR_NAME = "Corda.IdentityStateAndRefPostProcessor"
    }
    override fun postProcess(inputs: Stream<StateAndRef<ContractState>>): Stream<StateAndRef<ContractState>> {
        return inputs
    }
    override val name = POST_PROCESSOR_NAME
    override val availableForRPC = true
}