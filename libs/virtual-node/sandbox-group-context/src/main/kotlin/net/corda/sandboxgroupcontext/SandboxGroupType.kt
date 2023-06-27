package net.corda.sandboxgroupcontext

import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification

/**
 * Enumeration of various sandbox group types.
 */
enum class SandboxGroupType(
    private val typeName: String,
    val serviceMarkerType: Class<*>,
    val hasInjection: Boolean
) {
    FLOW("flow", UsedByFlow::class.java, hasInjection = true),
    VERIFICATION("verification", UsedByVerification::class.java, hasInjection = false),
    PERSISTENCE("persistence", UsedByPersistence::class.java, hasInjection = false);

    override fun toString(): String = typeName

    init {
        require(serviceMarkerType.isInterface) {
            "Service marker ${serviceMarkerType.name} must be an interface"
        }
    }
}
