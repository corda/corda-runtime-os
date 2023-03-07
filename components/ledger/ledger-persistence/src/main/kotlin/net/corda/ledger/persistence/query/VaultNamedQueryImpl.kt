package net.corda.ledger.persistence.query

import net.corda.v5.ledger.common.query.VaultNamedQuery
import net.corda.v5.ledger.common.query.VaultNamedQueryCollector
import net.corda.v5.ledger.common.query.VaultNamedQueryFilter
import net.corda.v5.ledger.common.query.VaultNamedQueryTransformer

data class VaultNamedQueryImpl(
    private val name: String,
    private val jsonString: String?,
    private val filter: VaultNamedQueryFilter<*>?,
    private val mapper: VaultNamedQueryTransformer<*, *>?,
    private val collector: VaultNamedQueryCollector<*, *>?
) : VaultNamedQuery {

    override fun getName(): String = name

    override fun getJsonString(): String? = jsonString

    override fun getFilter(): VaultNamedQueryFilter<*>? = filter

    override fun getMapper(): VaultNamedQueryTransformer<*, *>? = mapper

    override fun getCollector(): VaultNamedQueryCollector<*, *>? = collector
}
