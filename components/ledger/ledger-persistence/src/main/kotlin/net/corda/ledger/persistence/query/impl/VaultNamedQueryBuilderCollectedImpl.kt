package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderCollected
import org.slf4j.LoggerFactory

class VaultNamedQueryBuilderCollectedImpl(
    private val registry: VaultNamedQueryRegistry,
    private val vaultNamedQuery: VaultNamedQuery
) : VaultNamedQueryBuilderCollected {

    private companion object {
        private val logger = LoggerFactory.getLogger(VaultNamedQueryBuilderCollectedImpl::class.java)
    }

    override fun register() {
        logger.debug { "Registering custom query with name: ${vaultNamedQuery.name}" }

        registry.registerQuery(vaultNamedQuery)
    }
}
