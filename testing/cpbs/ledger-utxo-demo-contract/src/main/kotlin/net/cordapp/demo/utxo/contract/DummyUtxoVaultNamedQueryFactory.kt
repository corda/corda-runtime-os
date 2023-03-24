package net.cordapp.demo.utxo.contract

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer

/**
 * This is a dummy named query factory that will:
 * - Apply a dummy pass-through filter
 * - Apply a basic transformation into a String
 *
 * Neither the filtering nor the transforming logic makes any sense, it is only there to test functionality.
 */
@Suppress("unused")
class DummyUtxoVaultNamedQueryFactory : VaultNamedQueryFactory {
    @Suspendable
    override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory.create("UTXO_DUMMY_QUERY")
            .whereJson("WHERE custom_representation ->> 'temp' = :testField")
            .filter(DummyUtxoVaultNamedQueryFilter())
            .map(DummyUtxoVaultNamedQueryTransformer())
            .register()
    }

    class DummyUtxoVaultNamedQueryFilter : VaultNamedQueryFilter<TestUtxoState> {
        override fun filter(state: TestUtxoState, parameters: MutableMap<String, Any>): Boolean {
            return true
        }
    }

    class DummyUtxoVaultNamedQueryTransformer : VaultNamedQueryTransformer<TestUtxoState, String> {
        override fun transform(state: TestUtxoState, parameters: MutableMap<String, Any>): String {
            return state.testField
        }
    }
}
