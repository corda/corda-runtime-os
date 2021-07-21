package net.corda.sandbox.cache

import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.sandbox.SandboxGroup
import java.nio.file.Path

data class FlowMetadata(val name: String, val key: FlowKey)

interface SandboxCache {
    fun getSandboxGroupFor(identity: HoldingIdentity, flow: FlowMetadata): SandboxGroup
//    fun getSandboxById(id: FlowMetadata): SandboxGroup

    fun loadCpbs(CPBs: List<Path>)
}
