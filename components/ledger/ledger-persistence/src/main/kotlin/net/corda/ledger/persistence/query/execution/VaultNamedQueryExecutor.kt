package net.corda.ledger.persistence.query.execution

import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.v5.application.persistence.PagedQuery
import java.nio.ByteBuffer

interface VaultNamedQueryExecutor {

    fun executeQuery(
        holdingIdentity: HoldingIdentity,
        request: ExecuteVaultNamedQueryRequest
    ): PagedQuery.ResultSet<ByteBuffer>

}
