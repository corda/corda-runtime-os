package net.corda.ledger.verification.sandbox

import net.corda.ledger.utxo.verification.CordaPackageSummary
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity

interface VerificationSandboxService {

    /**
     * Get (or create) the verification sandbox for the given holding identity and CPKs
     *
     * @throws [CordaRuntimeException] if not found
     */
    fun get(holdingIdentity: HoldingIdentity, cpks: List<CordaPackageSummary>): SandboxGroupContext
}
