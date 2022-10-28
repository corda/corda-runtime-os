package net.corda.persistence.common

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

interface EntitySandboxService {

    /**
     * Adds a custom initialisation step for the service to use when initializing a new sandbox.
     *
     * @param initFunction initialization function to be called when a new sandbox is created.
     */
    fun addInitialisationStep(
        initFunction: (
            cpks: Collection<CpkMetadata>,
            virtualNode: VirtualNodeInfo,
            ctx: MutableSandboxGroupContext
        ) -> AutoCloseable
    )

    /**
     * Get (or create) the entity (db) sandbox for the given holding identity
     *
     * @throws [CordaRuntimeException] if not found
     */
    fun get(holdingIdentity: HoldingIdentity): SandboxGroupContext
}
