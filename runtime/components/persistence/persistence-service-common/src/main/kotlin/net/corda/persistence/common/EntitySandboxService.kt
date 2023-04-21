package net.corda.persistence.common

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

interface EntitySandboxService {

    /**
     * Get (or create) the entity (db) sandbox for the given holding identity
     *
     * @throws [CordaRuntimeException] if not found
     */
    fun get(holdingIdentity: HoldingIdentity, cpkFileHashes: Set<SecureHash>): SandboxGroupContext
}
