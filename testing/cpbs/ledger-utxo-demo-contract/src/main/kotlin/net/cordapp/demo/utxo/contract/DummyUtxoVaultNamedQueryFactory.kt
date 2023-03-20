package net.cordapp.demo.utxo.contract

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory

@Suppress("unused")
class DummyUtxoVaultNamedQueryFactory : VaultNamedQueryFactory {
    @Suspendable
    override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory.create("UTXO_DUMMY_QUERY")
            .whereJson("WHERE custom_representation ->> 'temp' = :testField")
            .register()
    }
}
