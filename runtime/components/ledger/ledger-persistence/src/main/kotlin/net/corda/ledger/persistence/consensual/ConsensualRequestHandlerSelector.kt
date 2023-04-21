package net.corda.ledger.persistence.consensual

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.sandboxgroupcontext.SandboxGroupContext

interface ConsensualRequestHandlerSelector{
    fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest) : RequestHandler
}
