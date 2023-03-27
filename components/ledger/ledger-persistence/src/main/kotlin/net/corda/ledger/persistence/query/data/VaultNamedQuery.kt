package net.corda.ledger.persistence.query.data

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefFilter
import net.corda.v5.ledger.utxo.query.VaultNamedQueryStateAndRefTransformer

/**
 * A data class representing a named ledger query. A named ledger query must have a name associated with it,
 * the rest of the fields are optional.
 */
data class VaultNamedQuery(
    val name: String,
    val query: ParsedQuery,
    val filter: VaultNamedQueryStateAndRefFilter<ContractState>?,
    val mapper: VaultNamedQueryStateAndRefTransformer<ContractState, Any>?,
    val collector: VaultNamedQueryCollector<Any, Any>?
) {
    data class ParsedQuery(val originalQuery: String, val query: String, val type: Type)

    enum class Type {
        WHERE_JSON
    }
}
