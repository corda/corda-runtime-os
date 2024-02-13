package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
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

    private var query: String? = null
    private var filter: VaultNamedQueryFilter<*>? = null
    private var transformer: VaultNamedQueryTransformer<*, *>? = null

    private val orderByFragments: MutableList<Pair<String, String?>> = mutableListOf()
    private var unconsumedStatesOnly: Boolean = false

    private companion object {
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda_TimestampLimit"
    }

    override fun whereJson(query: String): VaultNamedQueryBuilder {
        this.query = query
        return this
    }

    override fun orderBy(columnExpression: String, flags: String?): VaultNamedQueryBuilder {
        orderByFragments.add(columnExpression to flags)
        return this
    }

    override fun orderBy(columnExpression: String): VaultNamedQueryBuilder {
        orderByFragments.add(columnExpression to null)
        return this
    }

    override fun selectUnconsumedStatesOnly(): VaultNamedQueryBuilder {
        unconsumedStatesOnly = true
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
        // TODO These casts are necessary because using `Any` in `VaultNamedQuery` will result in a compilation error
        @Suppress("unchecked_cast")
        return VaultNamedQueryBuilderCollectedImpl(
            vaultNamedQueryRegistry,
            VaultNamedQuery(
                name,
                prepareQuery(),
                filter as? VaultNamedQueryFilter<Any>,
                transformer as? VaultNamedQueryTransformer<Any, Any>,
                collector as? VaultNamedQueryCollector<Any, Any>,
                parseOrderBy()
            )
        )
    }

    override fun register() {
        val parsedQuery = prepareQuery()

        logQueryRegistration(name, parsedQuery)

        // TODO These casts are necessary because using `Any` in `VaultNamedQuery` will result in a compilation error
        @Suppress("unchecked_cast")
        vaultNamedQueryRegistry.registerQuery(
            VaultNamedQuery(
                name,
                parsedQuery,
                filter as? VaultNamedQueryFilter<Any>,
                transformer as? VaultNamedQueryTransformer<Any, Any>,
                collector = null,
                parseOrderBy()
            )
        )
    }

    private fun prepareQuery(): VaultNamedQuery.ParsedQuery {
        val notNullQuery =
            "${requireNotNull(query) { "Vault named query: $name does not have its query statement set" }}${
                if (unconsumedStatesOnly) {
                    " AND (visible_states.consumed IS NULL OR visible_states.consumed >= :${TIMESTAMP_LIMIT_PARAM_NAME})"
                } else {
                    ""
                }
            }"

        return VaultNamedQuery.ParsedQuery(
            notNullQuery,
            vaultNamedQueryParser.parseWhereJson(notNullQuery),
            VaultNamedQuery.Type.WHERE_JSON
        )
    }

    private fun parseOrderBy(): VaultNamedQuery.ParsedQuery? {
        if (orderByFragments.isEmpty()) {
            return null
        }
        val parsed = mutableListOf<String>()
        val original = mutableListOf<String>()

        orderByFragments.forEach {
            parsed.add("${vaultNamedQueryParser.parseSimpleExpression(it.first)}${it.second?.let{value -> " $value"} ?: ""}")
            original.add("${it.first}${it.second?.let{value -> " $value"} ?: ""}")
        }
        return VaultNamedQuery.ParsedQuery(original.joinToString(", "), parsed.joinToString(", "), VaultNamedQuery.Type.ORDER_BY)
    }
}
