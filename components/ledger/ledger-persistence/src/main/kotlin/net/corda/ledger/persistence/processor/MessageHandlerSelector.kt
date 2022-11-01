package net.corda.ledger.persistence.processor

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.common.MessageHandler
import net.corda.sandboxgroupcontext.SandboxGroupContext

interface MessageHandlerSelector {
    fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest) : MessageHandler
}

