package com.r3.corda.demo.utxo.contract

import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefTransformer
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory

/**
 * This is a dummy named query factory that will:
 * - Apply a dummy pass-through filter
 * - Apply a basic transformation into a String
 *
 * Neither the filtering nor the transforming logic makes any sense, it is only there to test functionality.
 */
@Suppress("unused")
class DummyUtxoVaultNamedQueryFactory : VaultNamedQueryFactory {
    override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory.create("UTXO_DUMMY_QUERY")
            .whereJson(
                "WHERE visible_states.custom_representation -> 'com.r3.corda.demo.utxo.contract.TestUtxoState' " +
                        "->> 'testField' = :testField"
            )
            .filter(DummyUtxoVaultNamedQueryFilter())
            .map(DummyUtxoVaultNamedQueryTransformer())
            .register()
    }

    class DummyUtxoVaultNamedQueryFilter : VaultNamedQueryStateAndRefFilter<TestUtxoState> {
        override fun filter(state: StateAndRef<TestUtxoState>, parameters: MutableMap<String, Any?>): Boolean {
            return true
        }
    }

    class DummyUtxoVaultNamedQueryTransformer : VaultNamedQueryStateAndRefTransformer<TestUtxoState, String> {
        override fun transform(state: StateAndRef<TestUtxoState>, parameters: MutableMap<String, Any?>): String {
            return state.state.contractState.testField
        }
    }
}
