package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer
import org.slf4j.LoggerFactory

class VaultNamedQueryBuilderImpl(
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry
) : VaultNamedQueryBuilder {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var name: String? = null
    private var whereJson: String? = null
    private var filter: VaultNamedQueryFilter<*>? = null
    private var mapper: VaultNamedQueryTransformer<*, *>? = null
    private var collector: VaultNamedQueryCollector<*, *>? = null

    @Suspendable
    override fun whereJson(json: String): VaultNamedQueryBuilder {
        this.whereJson = json
        return this
    }
    @Suspendable
    override fun filter(filter: VaultNamedQueryFilter<*>): VaultNamedQueryBuilder {
        this.filter = filter
        return this
    }
    @Suspendable
    override fun map(mapper: VaultNamedQueryTransformer<*, *>): VaultNamedQueryBuilder {
        this.mapper = mapper
        return this
    }
    @Suspendable
    override fun collect(collector: VaultNamedQueryCollector<*, *>): VaultNamedQueryBuilder {
        this.collector = collector
        return this
    }
    @Suspendable
    override fun register() {
        if (logger.isDebugEnabled) {
            logger.debug("Registering custom query with name: $name")
        }
        require(name != null) {
            "Named ledger query can't be registered without a name."
        }

        vaultNamedQueryRegistry.registerQuery(
            VaultNamedQueryImpl(
                name!!,
                whereJson,
                filter,
                mapper,
                collector
            )
        )
    }
}