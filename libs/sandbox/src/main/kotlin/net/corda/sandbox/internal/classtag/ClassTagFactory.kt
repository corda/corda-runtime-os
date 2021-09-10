package net.corda.sandbox.internal.classtag

import net.corda.sandbox.Sandbox
import org.osgi.framework.Bundle

/** Creates, serialises and deserialises [ClassTag] objects. */
internal interface ClassTagFactory {
    /** Creates and serialises a [ClassTag]. */
    fun createSerialised(
        isStaticClassTag: Boolean,
        isPlatformBundle: Boolean,
        bundle: Bundle,
        sandbox: Sandbox
    ): String

    /** Deserialises a [ClassTag]. */
    fun deserialise(serialisedClassTag: String): ClassTag
}