package net.corda.sandboxgroupcontext

import net.corda.packaging.CPK
import net.corda.virtualnode.HoldingIdentity

/**
 * This is the unique key for looking up [SandboxGroupContext] objects.
 *
 * We can have several sandbox group _context_ types so [SandboxGroupType]
 * distinguishes between the various types, e.g. some sandbox-group types require
 * access to fewer bundles.
 */
data class VirtualNodeContext(
    val holdingIdentity: HoldingIdentity,
    val cpkIdentifiers: Set<CPK.Identifier>,
    val sandboxGroupType: SandboxGroupType,
)
