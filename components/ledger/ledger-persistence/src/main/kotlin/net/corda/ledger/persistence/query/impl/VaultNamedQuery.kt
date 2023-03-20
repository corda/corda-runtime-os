package net.corda.ledger.persistence.query.impl

import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer

/**
 * A data class representing a named ledger query. A named ledger query must have a name associated with it,
 * the rest of the fields are optional.
 */
data class VaultNamedQuery(
    val name: String,
    val jsonString: String?,
    val filter: VaultNamedQueryFilter<*>?,
    val mapper: VaultNamedQueryTransformer<*, *>?,
    val collector: VaultNamedQueryCollector<*, *>?
)
