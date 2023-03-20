package net.corda.ledger.persistence.query.execution

import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.lifecycle.Lifecycle
import net.corda.v5.application.persistence.PagedQuery
import java.nio.ByteBuffer

interface VaultNamedQueryExecutor : Lifecycle {

    fun executeQuery(
        request: ExecuteVaultNamedQueryRequest
    ): PagedQuery.ResultSet<ByteBuffer>

}
