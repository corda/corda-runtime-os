package net.corda.ledger.persistence.query.registration.impl

import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefTransformer
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderCollected
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
    private var transformer: VaultNamedQueryTransformer<*, *>? = null

    override fun whereJson(json: String): VaultNamedQueryBuilder {
        this.whereJson = json
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

        // TODO These casts are necessary because using `out ContractState` in `VaultNamedQuery`
        //  will result in a compilation error
        @Suppress("unchecked_cast")
        return VaultNamedQueryBuilderCollectedImpl(
            vaultNamedQueryRegistry,
            VaultNamedQuery(
                name,
                whereJson,
                filter as VaultNamedQueryStateAndRefFilter<ContractState>,
                transformer as VaultNamedQueryStateAndRefTransformer<ContractState, Any>,
                collector as VaultNamedQueryCollector<Any, Any>
            )
        )
    }

    override fun register() {
        logger.debug { "Registering custom query with name: $name" }

        // TODO These casts are necessary because using `out ContractState` in `VaultNamedQuery`
        //  will result in a compilation error
        @Suppress("unchecked_cast")
        vaultNamedQueryRegistry.registerQuery(
            VaultNamedQuery(
            name,
            whereJson,
            filter as VaultNamedQueryStateAndRefFilter<ContractState>,
            transformer as VaultNamedQueryStateAndRefTransformer<ContractState, Any>,
            null
        ))
    }
}