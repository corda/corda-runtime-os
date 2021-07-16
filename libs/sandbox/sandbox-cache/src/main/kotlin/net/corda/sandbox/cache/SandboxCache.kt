package net.corda.sandbox.cache

import net.corda.data.identity.HoldingIdentity
import net.corda.packaging.Cpk
import net.corda.sandbox.Sandbox

data class FlowId(val cpkId: Cpk.Identifier, val name: String)

interface SandboxCache {
    fun getSandboxFor(identity: HoldingIdentity, flow: FlowId): Sandbox
}
