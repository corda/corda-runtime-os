package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderCollected

class VaultNamedQueryBuilderCollectedImpl(
    private val registry: VaultNamedQueryRegistry,
    private val vaultNamedQuery: VaultNamedQuery
) : VaultNamedQueryBuilderCollected {

    override fun register() {
        logQueryRegistration(vaultNamedQuery.name, vaultNamedQuery.query)
        registry.registerQuery(vaultNamedQuery)
    }
}
