package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer
import org.slf4j.LoggerFactory

class VaultNamedQueryBuilderImpl(
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry,
    private val name: String
) : VaultNamedQueryBuilder {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var whereJson: String? = null
    private var filter: VaultNamedQueryFilter<*>? = null
    private var mapper: VaultNamedQueryTransformer<*, *>? = null
    private var collector: VaultNamedQueryCollector<*, *>? = null

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

    override fun collect(collector: VaultNamedQueryCollector<*, *>): VaultNamedQueryBuilder {
        this.collector = collector
        return this
    }

    override fun register() {
        logger.debug { "Registering custom query with name: $name" }

        vaultNamedQueryRegistry.registerQuery(
            VaultNamedQuery(
                name,
                whereJson,
                filter,
                mapper,
                collector
            )
        )
    }
}