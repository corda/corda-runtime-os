package net.corda.ledger.persistence.query

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.query.VaultNamedQuery

/**
 * An interface representing a named query storage.
 */
@DoNotImplement
interface VaultNamedQueryRegistry {

    /**
     * @param name The name of the named query that needs to be accessed
     * @return The named query object associated with the given name or null if it was not found in the registry
     */
    @Suspendable
    fun getQuery(name: String): VaultNamedQuery?

    /**
     * @param query The query that needs to be registered/stored.
     */
    @Suspendable
    fun registerQuery(query: VaultNamedQuery)
}