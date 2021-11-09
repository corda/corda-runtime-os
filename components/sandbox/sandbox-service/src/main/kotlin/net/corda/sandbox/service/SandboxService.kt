package net.corda.sandbox.service

import net.corda.lifecycle.Lifecycle
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer

/**
 * Placeholder until sandbox/cpi team introduce something better
 *
 */
interface SandboxService : Lifecycle {

    /**
     * Get the sandbox for a given [cpiId], [identity] and [sandboxType].
     * Creates a new sandbox if it doesn't already exist.
     */
    fun getSandboxGroupFor(cpiId: String, identity: String, sandboxType: SandboxType): SandboxGroup

    /**
     * Get the checkpoint serializer for a [sandboxGroup]
     */
    fun getSerializerForSandbox(sandboxGroup: SandboxGroup): CheckpointSerializer
}
