package net.corda.ledger.persistence.query.execution

import net.corda.data.identity.HoldingIdentity
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.data.persistence.EntityResponse

interface VaultNamedQueryExecutor {

    fun executeQuery(
        holdingIdentity: HoldingIdentity,
        request: ExecuteVaultNamedQueryRequest
    ): EntityResponse

}
