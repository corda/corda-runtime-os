package net.corda.ledger.verification.sandbox

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

interface VerificationSandboxService {

    /**
     * Get (or create) the verification sandbox for the given holding identity and CPKs
     *
     * @throws [CordaRuntimeException] if not found
     */
    fun get(holdingIdentity: HoldingIdentity, cpkFileChecksums: Set<SecureHash>): SandboxGroupContext
}
