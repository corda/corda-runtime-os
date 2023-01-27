package net.corda.ledger.verification.sanbox

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity

interface VerificationSandboxService {

    /**
     * Get (or create) the verification sandbox for the given holding identity and CPKs
     *
     * @throws [CordaRuntimeException] if not found
     */
    fun get(holdingIdentity: HoldingIdentity, cpiId: CpiIdentifier): SandboxGroupContext
}
