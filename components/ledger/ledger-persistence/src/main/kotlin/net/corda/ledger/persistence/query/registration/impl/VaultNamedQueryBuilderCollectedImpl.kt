package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderCollected

class VaultNamedQueryBuilderCollectedImpl(
    private val registry: VaultNamedQueryRegistry,
    private val vaultNamedQuery: VaultNamedQuery
) : VaultNamedQueryBuilderCollected {

    override fun register() {
        logQueryRegistration(vaultNamedQuery.name, vaultNamedQuery.query)
        registry.registerQuery(vaultNamedQuery)
    }
}
