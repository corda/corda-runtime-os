package net.corda.ledger.persistence.processor

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.sandboxgroupcontext.SandboxGroupContext

interface DelegatedRequestHandlerSelector {
    fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler
}
