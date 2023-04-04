package net.corda.ledger.persistence.json

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory

/**
 * A storage for contract state json factories.
 */
interface ContractStateVaultJsonFactoryStorage {

    /**
     * Store a given contract state json factory.
     */
    fun registerJsonFactory(factory: ContractStateVaultJsonFactory<ContractState>)

    /**
     * Return all the contract state json factories that hierarchically belong to a given state.
     */
    fun getFactoriesForClass(state: ContractState): List<ContractStateVaultJsonFactory<ContractState>>
}
