package net.corda.components.sandbox.service

import net.corda.lifecycle.Lifecycle
import net.corda.sandbox.SandboxGroup

interface SandboxService : Lifecycle {

    /**
     * Get the sandbox for a given [cpiId] and [flowName].
     * Create a new sandbox if it doesn't already exist.
     */
    fun getSandboxGroupFor(cpiId: String, flowName: String): SandboxGroup
}
