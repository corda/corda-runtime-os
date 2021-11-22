package net.corda.virtualnode.sandboxgroup

import net.corda.packaging.CPI
import net.corda.virtual.node.info.HoldingIdentity

// cpiIdentifier may become list of cpk ids.
data class VirtualNodeContext(
    val holdingIdentity: HoldingIdentity,
    val cpiIdentifier: CPI.Identifier,
    val sandboxGroupType: SandboxGroupType,
)
