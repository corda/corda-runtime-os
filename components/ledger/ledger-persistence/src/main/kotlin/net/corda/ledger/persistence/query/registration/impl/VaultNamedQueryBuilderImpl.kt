package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderCollected

class VaultNamedQueryBuilderImpl(
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry,
    private val vaultNamedQueryParser: VaultNamedQueryParser,
    private val name: String
) : VaultNamedQueryBuilder {

    private var query: VaultNamedQuery.ParsedQuery? = null
    private var filter: VaultNamedQueryFilter<*>? = null
    private var transformer: VaultNamedQueryTransformer<*, *>? = null

    override fun whereJson(query: String): VaultNamedQueryBuilder {
        this.query = VaultNamedQuery.ParsedQuery(
            originalQuery = query,
            query = vaultNamedQueryParser.parseWhereJson(query),
            type = VaultNamedQuery.Type.WHERE_JSON
        )
        return this
    }

    override fun filter(filter: VaultNamedQueryFilter<*>): VaultNamedQueryBuilder {
        require(this.filter == null) {
            "Filter function has already been set!"
        }
        this.filter = filter
        return this
    }

    override fun map(transformer: VaultNamedQueryTransformer<*, *>): VaultNamedQueryBuilder {
        require(this.transformer == null) {
            "Mapper function has already been set!"
        }
        this.transformer = transformer
        return this
    }


    override fun collect(collector: VaultNamedQueryCollector<*, *>): VaultNamedQueryBuilderCollected {

        val notNullQuery = requireNotNull(query) { "Vault named query: $name does not have its query statement set" }

        // TODO These casts are necessary because using `Any` in `VaultNamedQuery` will result in a compilation error
        @Suppress("unchecked_cast")
        return VaultNamedQueryBuilderCollectedImpl(
            vaultNamedQueryRegistry,
            VaultNamedQuery(
                name,
                notNullQuery,
                filter as? VaultNamedQueryFilter<Any>,
                transformer as? VaultNamedQueryTransformer<Any, Any>,
                collector as? VaultNamedQueryCollector<Any, Any>
            )
        )
    }

    override fun register() {
        val notNullQuery = requireNotNull(query) { "Vault named query: $name does not have its query statement set" }

        logQueryRegistration(name, notNullQuery)

        // TODO These casts are necessary because using `Any` in `VaultNamedQuery` will result in a compilation error
        @Suppress("unchecked_cast")
        vaultNamedQueryRegistry.registerQuery(
            VaultNamedQuery(
                name,
                notNullQuery,
                filter as? VaultNamedQueryFilter<Any>,
                transformer as? VaultNamedQueryTransformer<Any, Any>,
                collector = null
            )
        )
    }
}
