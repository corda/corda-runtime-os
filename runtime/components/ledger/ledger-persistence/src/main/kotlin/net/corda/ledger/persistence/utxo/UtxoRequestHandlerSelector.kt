package net.corda.ledger.persistence.utxo

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.sandboxgroupcontext.SandboxGroupContext

interface UtxoRequestHandlerSelector {
    fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest) : RequestHandler
}
