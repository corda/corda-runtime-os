package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderCollected
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer
import org.slf4j.LoggerFactory

class VaultNamedQueryBuilderImpl(
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry,
    private val name: String
) : VaultNamedQueryBuilder {

    private companion object {
        private val logger = LoggerFactory.getLogger(VaultNamedQueryBuilderImpl::class.java)
    }

    private var whereJson: String? = null
    private var filter: VaultNamedQueryFilter<*>? = null
    private var mapper: VaultNamedQueryTransformer<*, *>? = null

    override fun whereJson(json: String): VaultNamedQueryBuilder {
        this.whereJson = json
        return this
    }

    override fun filter(filter: VaultNamedQueryFilter<*>): VaultNamedQueryBuilder {
        this.filter = filter
        return this
    }

    override fun map(mapper: VaultNamedQueryTransformer<*, *>): VaultNamedQueryBuilder {
        this.mapper = mapper
        return this
    }

    override fun collect(collector: VaultNamedQueryCollector<*, *>): VaultNamedQueryBuilderCollected {
        return VaultNamedQueryBuilderCollectedImpl(
            vaultNamedQueryRegistry,
            VaultNamedQuery(
                name,
                whereJson,
                filter,
                mapper,
                collector
            )
        )
    }

    override fun register() {
        logger.debug { "Registering custom query with name: $name" }

        vaultNamedQueryRegistry.registerQuery(VaultNamedQuery(
            name,
            whereJson,
            filter,
            mapper,
            null
        ))
    }
}