package net.corda.ledger.persistence.query.execution

import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery

interface VaultNamedQueryExecutor {

    fun executeQuery(
        holdingIdentity: HoldingIdentity,
        request: FindWithNamedQuery
    ): EntityResponse

}
