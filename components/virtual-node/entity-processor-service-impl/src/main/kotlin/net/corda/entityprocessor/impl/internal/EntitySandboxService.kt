package net.corda.entityprocessor.impl.internal

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity

interface EntitySandboxService {
    /**
     * Get (or create) the entity (db) sandbox for the given holding identity
     *
     * @throws [CordaRuntimeException] if not found
     */
    fun get(holdingIdentity: HoldingIdentity): SandboxGroupContext
}
