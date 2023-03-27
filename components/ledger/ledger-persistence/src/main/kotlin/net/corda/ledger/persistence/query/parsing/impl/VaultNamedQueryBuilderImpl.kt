package net.corda.ledger.persistence.query.parsing.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderCollected
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer

class VaultNamedQueryBuilderImpl(
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry,
    private val vaultNamedQueryParser: VaultNamedQueryParser,
    private val name: String
) : VaultNamedQueryBuilder {

    private var query: VaultNamedQuery.ParsedQuery? = null
    private var filter: VaultNamedQueryFilter<*>? = null
    private var mapper: VaultNamedQueryTransformer<*, *>? = null

    override fun whereJson(query: String): VaultNamedQueryBuilder {
        this.query = VaultNamedQuery.ParsedQuery(
            originalQuery = query,
            query = vaultNamedQueryParser.parseWhereJson(query),
            type = VaultNamedQuery.Type.WHERE_JSON
        )
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
        val notNullQuery = requireNotNull(query) { "Vault named query: $name does not have its query statement set" }
        return VaultNamedQueryBuilderCollectedImpl(
            vaultNamedQueryRegistry,
            VaultNamedQuery(
                name,
                notNullQuery,
                filter,
                mapper,
                collector
            )
        )
    }

    override fun register() {
        val notNullQuery = requireNotNull(query) { "Vault named query: $name does not have its query statement set" }
        logQueryRegistration(name, notNullQuery)
        vaultNamedQueryRegistry.registerQuery(
            VaultNamedQuery(
                name,
                notNullQuery,
                filter,
                mapper,
                collector = null
            )
        )
    }
}