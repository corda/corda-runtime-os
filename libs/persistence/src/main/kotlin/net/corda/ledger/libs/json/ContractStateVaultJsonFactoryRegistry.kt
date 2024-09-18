package net.corda.ledger.libs.json

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory

/**
 * A storage for contract state json factories.
 */
interface ContractStateVaultJsonFactoryRegistry {

    /**
     * Store a given contract state json factory.
     */
    fun registerJsonFactory(factory: ContractStateVaultJsonFactory<out ContractState>)

    /**
     * Return all the contract state json factories that hierarchically belong to a given state.
     */
    fun getFactoriesForClass(state: ContractState): List<ContractStateVaultJsonFactory<out ContractState>>
}
