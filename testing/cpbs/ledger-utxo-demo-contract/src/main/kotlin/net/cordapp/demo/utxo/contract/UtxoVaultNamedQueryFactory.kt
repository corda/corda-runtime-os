package net.cordapp.demo.utxo.contract

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderFactory
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFactory

@Suppress("unused")
class UtxoVaultNamedQueryFactory : VaultNamedQueryFactory {
    @Suspendable
    override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
        vaultNamedQueryBuilderFactory.create("UTXO_DUMMY_QUERY")
            // TODO This is just a dummy where clause for now and has absolutely no effect
            .whereJson("WHERE custom ->> 'TestUtxoState.testField' = :testField")
            .register()
    }
}
