package net.corda.sandboxgroupcontext

import net.corda.libs.packaging.CpkIdentifier
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
    val cpkIdentifiers: Set<CpkIdentifier>,
    val sandboxGroupType: SandboxGroupType,
    val serviceMarkerType: Class<*>,
    val serviceFilter: String?
) {
    init {
        require(serviceMarkerType.isInterface) {
            "Service marker ${serviceMarkerType.name} must be an interface"
        }
    }
}
