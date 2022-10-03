package net.corda.sandboxgroupcontext

import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification

/**
 * Enumeration of various sandbox group types.
 */
enum class SandboxGroupType(val serviceMarkerType: Class<*>) {
    FLOW(UsedByFlow::class.java),
    VERIFICATION(UsedByVerification::class.java),
    PERSISTENCE(UsedByPersistence::class.java);

    init {
        require(serviceMarkerType.isInterface) {
            "Service marker ${serviceMarkerType.name} must be an interface"
        }
    }
}
