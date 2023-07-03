package net.corda.sandboxgroupcontext

import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification

/**
 * Enumeration of various sandbox group types.
 */
enum class SandboxGroupType(
    private val typeName: String,

    /**
     * Marker interface for all OSGi `PROTOTYPE` services
     * that Corda must create for this sandbox type.
     */
    val serviceMarkerType: Class<*>,

    /**
     * Does this sandbox type support `@CordaInject`?
     */
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
