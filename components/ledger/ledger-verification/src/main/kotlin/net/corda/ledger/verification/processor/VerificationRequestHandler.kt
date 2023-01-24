package net.corda.ledger.verification.processor

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.messaging.api.records.Record
import net.corda.sandboxgroupcontext.SandboxGroupContext

interface VerificationRequestHandler {

    fun handleRequest(sandbox: SandboxGroupContext, request: VerifyContractsRequest): Record<*, *>
}