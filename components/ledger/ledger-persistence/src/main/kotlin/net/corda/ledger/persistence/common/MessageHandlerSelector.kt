package net.corda.ledger.persistence.common

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.sandboxgroupcontext.SandboxGroupContext

interface MessageHandlerSelector {
    fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest) : MessageHandler
}

