package net.corda.ledger.persistence.query

import net.corda.ledger.persistence.query.impl.VaultNamedQuery
import net.corda.v5.base.annotations.DoNotImplement

/**
 * An interface representing a named query storage.
 */
@DoNotImplement
interface VaultNamedQueryRegistry {

    /**
     * @param name The name of the named query that needs to be accessed.
     * @return The named query object associated with the given name or null if it was not found in the registry.
     */
    fun getQuery(name: String): VaultNamedQuery?

    /**
     * @param query The query that needs to be registered/stored.
     */
    fun registerQuery(query: VaultNamedQuery)
}